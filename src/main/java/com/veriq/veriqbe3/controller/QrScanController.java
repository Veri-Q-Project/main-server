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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Value;

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
            QrScanResponse response = qrScanRedisService.processWithRedis(image, guestUuid);
               return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }
    //  스캔 내역 및 로딩화면 후 상세 보고서 API 엔드포인트
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

    // ML 서버가 분석을 마치고 결과를 던져주는 콜백 API
    @Value("${ml.server.secret}")
    private String mlServerSecret;
    @PostMapping("/callback")
    public ResponseEntity<String> analysisCallback(
            @RequestHeader(value = "guest_uuid", required = false) String guestUuid,
            @RequestHeader(value = "X-ML-Secret", required = false) String providedSecret,
            @RequestBody AnalysisResponse resultDto) {

        try {
            if (providedSecret == null || !providedSecret.equals(mlServerSecret)) {
                log.warn("🚨 [보안 경고] 유효하지 않은 콜백 요청 접근 시도! URL: {}", resultDto.originalUrl());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("접근 권한이 없습니다 (Invalid Secret).");
            }
            log.info("ML 서버로부터 분석 완료 콜백 수신 - URL: {}", resultDto.originalUrl());

            // 1. 서비스 계층에 넘겨서 DB 저장 및 Redis 캐싱을 한 번에 처리!
            // 2. 비밀번호가 맞을 때만 DB 저장 및 캐싱을 진행
            qrScanRedisService.saveAndCacheAnalysisResult(resultDto, guestUuid);



            // 3. 가흔 님 서버에게 "잘 받았어!" 라고 200 OK 응답
            return ResponseEntity.ok("DB 저장 및 캐싱 완벽하게 완료!");

        } catch (Exception e) {
            log.error("콜백 데이터 처리 중 에러 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("콜백 처리 실패: " + e.getMessage());
        }
    }

    // 프론트엔드가 파이프를 꽂으러 오는 곳
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@RequestParam("guest_uuid") String guestUuid) {

        // 타임아웃 5분(300,000ms)짜리 파이프 생성
        SseEmitter emitter = qrScanRedisService.createEmitter(guestUuid);

        return ResponseEntity.ok(emitter);
    }
}