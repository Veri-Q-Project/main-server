package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.service.ProcessQrScan;
import com.veriq.veriqbe3.service.QrScanRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController //json반환 컨트롤러
@RequestMapping("/api/v1/scan")     //공통 경로
@RequiredArgsConstructor    //api 객체 생성
public class QrScanController {

    private final ProcessQrScan processQrScan;  //의존성 주입
    private final QrScanRedisService qrScanRedisService; //   서비스 의존성 주입

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) //multipart_form_data 제한
    public ResponseEntity<QrScanResponse> uploadQrImage(    //반환 http body 타입 제한
        @RequestHeader(value = "guest_uuid", required = false ) String guestUuid,
        @RequestParam("image") MultipartFile image) {
        try {
            QrScanResponse response = processQrScan.process(image, guestUuid);
               return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
    //  스캔 내역 상세 조회 API 엔드포인트
    @GetMapping("/detail")
    public ResponseEntity<?> getUrlDetail(@RequestParam("url") String url) {
        try {
            log.info("상세 분석 결과 요청 들어옴 - URL: {}", url);

            //  Look-Aside 캐시 로직 호출
            AnalysisResponse response = qrScanRedisService.getUrlDetail(url);

            if (response == null) {
                // 캐시에도 없고 DB에도 없는 경우 (분석 중이거나 아예 없는 URL)
                log.warn("URL 분석 결과를 찾을 수 없습니다: {}", url);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("해당 URL의 분석 결과를 찾을 수 없습니다.");
            }

            // 성공 시 200 OK와 함께 데이터 쫙 뿌려주기
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("상세 조회 중 서버 에러 발생 - URL: {}", url, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 내부 오류가 발생했습니다.");
        }
    }
}