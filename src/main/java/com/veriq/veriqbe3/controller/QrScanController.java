package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.ProgressRequest;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.service.MlRequestService;
import com.veriq.veriqbe3.service.ProcessQrScan;
import com.veriq.veriqbe3.service.QrScanRedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController //json반환 컨트롤러
@RequestMapping("/api/v1/scan")     //공통 경로
@RequiredArgsConstructor    //api 객체 생성
public class QrScanController {

    //private final ProcessQrScan processQrScan;  //의존성 주입
    private final QrScanRedisService qrScanRedisService; //   서비스 의존성 주입
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    @Value("${ML_SECRET_KEY}")
    private String mlServerSecret;




    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) //multipart_form_data 제한
    public ResponseEntity<QrScanResponse> uploadQrImage(    //반환 http body 타입 제한
        @RequestHeader(value = "guest_uuid" ) String guestUuid,
        @RequestParam("image") MultipartFile image) {
        try {
            QrScanResponse response = qrScanRedisService.processWithRedis(image, guestUuid);
            return ResponseEntity.ok(response);


            //  프런트가 이상한 데이터를 보냈을 때 (사용자 잘못 -> 400)
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 QR 업로드 요청 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.badRequest().build();

//  파이썬 서버가 죽었거나, DB가 터졌을 때 (분석 서버 잘못 -> 502)
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("QR 스캔 요청 처리 중 서버(다운스트림) 에러 발생 - guestUuid: {}", guestUuid, e);
            // 파이썬 서버 통신 실패 시 (502 반환)
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        /// 스프링 서버 내부에서 뭔가 터졌을 때 (스프링 서버잘못 -> 500)
        catch (Exception e) {
            log.error("QR 스캔 요청 처리 중 서버 내부 에러 발생 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }



    }
    //  스캔 내역 및 로딩화면 후 상세 보고서 API 엔드포인트 ,프런트랑 연결
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




    // ML 서버가 분석을 마치고 분석최종결과를 던져주는 콜백 API

    @PostMapping("/callback")
    public ResponseEntity<String> analysisCallback(
            @RequestHeader(value = "guest_uuid", required = false) String guestUuid,
            @RequestHeader(value = "X-ML-Secret", required = false) String providedSecret,
            @RequestBody AnalysisResponse resultDto) {

        try {
            // [보안 로직] 상수 시간 비교(Constant-time comparison)를 통한 타이밍 공격 방어
            boolean isSecretValid = false;
            if (providedSecret != null && mlServerSecret != null) {
                byte[] providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
                byte[] expectedBytes = mlServerSecret.getBytes(StandardCharsets.UTF_8);

                // MessageDigest.isEqual은 중간에 틀려도 끝까지 비교하여 실행 시간을 일정하게 유지합니다.
                isSecretValid = MessageDigest.isEqual(providedBytes, expectedBytes);
            }

            if (!isSecretValid) {
                log.warn(" [보안 경고] 유효하지 않은 콜백 요청 접근 시도 (타이밍 공격 방어 적용됨)! URL: {}", resultDto.originalUrl());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("접근 권한이 없습니다 (Invalid Secret).");
            }
            log.info("ML 서버로부터 분석 완료 콜백 수신 - URL: {}", resultDto.originalUrl());

            // 1. 서비스 계층에 넘겨서 DB 저장 및 Redis 캐싱을 한 번에 처리!
            // 2. 비밀번호가 맞을 때만 DB 저장 및 캐싱을 진행
            String safeUuid = (guestUuid != null) ? guestUuid : "unknown";
            String idempotencyKey = "callbackdone:" + safeUuid + ":" + resultDto.originalUrl();
// 2. Redis에 방명록 쓰기 시도 (setIfAbsent = 없으면 쓰고 true, 있으면 안 쓰고 false 반환)
// TTL은 파이썬이 재시도할 만한 넉넉한 시간인 10분 정도로 줍니다.
            Boolean isFirst = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "PROCESSED", Duration.ofMinutes(10));

// 3. 이미 방명록에 이름이 있다면? (중복 콜백이면) 바로 돌려보냄!
            if (Boolean.FALSE.equals(isFirst)) {
                log.info("이미 처리된 중복 콜백 요청입니다. 무시합니다. - guestUuid: {}", guestUuid);
                return ResponseEntity.ok("이미 처리되었습니다.");
            }
            qrScanRedisService.saveAndCacheAnalysisResult(resultDto, guestUuid);
            // 3.스캔 히스토리 저장
            qrScanRedisService.saveHistoryToRedis(guestUuid, resultDto);


            //4.프런트로 완료 신호 전송


            try {
                // [STEP 1] COMPLETE 채널에 담을 통합 데이터 구성
                Map<String, Object> completePayload = new HashMap<>();
                completePayload.put("step", "분석 완료");
                completePayload.put("status", "COMPLETED");
                completePayload.put("message", "분석이 완료되었습니다. 결과 페이지로 이동합니다.");
                completePayload.put("originalUrl", resultDto.originalUrl()); // 이동할 목적지 URL
                completePayload.put("isUrl", true);

                // [STEP 2] JSON 문자열로 변환
                String jsonPayload = objectMapper.writeValueAsString(completePayload);

                // [STEP 3] COMPLETE 채널로 전송!
                // 이제 프론트는 이 JSON을 받아서 메시지도 띄우고, originalUrl로 이동도 시킵니다.
                qrScanRedisService.sendSseCompleteEvent(guestUuid, jsonPayload);

                log.info("ML 분석 완료 및 통합 COMPLETE 알림 전송 - User: {}", guestUuid);

            } catch (Exception e) {
                log.error("최종 COMPLETE 메시지 생성 실패", e);
            }




            // 4. ML 서버에게 "잘 받았어!" 라고 200 OK 응답
            return ResponseEntity.ok("DB 저장 및 캐싱 완벽하게 완료!");

        } catch (Exception e) {
            log.error("콜백 데이터 처리 중 에러 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("콜백 처리 실패: " + e.getMessage());
        }
    }

    // 파이썬 서버가 중간 단계가 끝날 때마다 쏴주는 중간보고 API
    @PostMapping("/callback/progress")
    public ResponseEntity<String> analysisProgressCallback(
             // 누구의 파이프인지 식별자 필수!
            @RequestHeader(value = "X-ML-Secret", required = false) String providedSecret,
            @Valid @RequestBody ProgressRequest request) { // { "step": "DOMAIN_CHECK", "message": "도메인 사칭 분석 완료" }
        log.info("🔔 [Progress 요청 도착] UUID: {}, 받은 Secret: {}", request.guestUuid(), providedSecret);

        try {
            // 1. 보안 검증 (기존 로직과 동일하게 유지)
            if (providedSecret == null) {
                log.warn("❌ [보안 실패] 파이썬이 Secret을 안 보냈습니다!");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("접근 권한이 없습니다.");
            }

            byte[] providedBytes = providedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] expectedBytes = mlServerSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);

// 길이가 다르거나, 내부 바이트가 하나라도 다르면 거절 (비교 시간은 항상 일정함)
            if (providedBytes.length != expectedBytes.length || !java.security.MessageDigest.isEqual(providedBytes, expectedBytes)) {
                log.warn("❌ [보안 실패] Secret 불일치! 기대값: [{}], 받은값: [{}]", mlServerSecret, providedSecret);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("접근 권한이 없습니다.");
            }
            // 2. 파이썬이 보내준 메시지 추출
            String ssePayload = objectMapper.writeValueAsString(request);

            // 3. 해당 guestUuid의 SSE 파이프를 찾아서 메시지 전송!
            // ( SseEmitter 서비스 호출)
            qrScanRedisService.sendSseEvent(request.guestUuid(), ssePayload);

            log.info("[SSE 중간 보고] User: {}, Step: {}, Status: {}",
                    request.guestUuid(), request.step(), request.status());
            return ResponseEntity.ok("상태 업데이트 성공!");

        } catch (Exception e) {
            String uuid = (request != null && request.guestUuid() != null) ? request.guestUuid() : "unknown";

            log.error("진행 상태 콜백 처리 중 에러 발생 - guestUuid: {}", uuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("에러 발생");
        }
    }

    // 프론트엔드가 파이프를 꽂으러 오는 곳
    @CrossOrigin(origins = "${frontend.url}")//프론트의 주소에 따라 유동, 해당 주소만 파이프 받아들임
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@RequestParam("guest_uuid") String guestUuid) {

        try {
            // 1. 파이프 생성 시도
            SseEmitter emitter = qrScanRedisService.createEmitter(guestUuid);
            return ResponseEntity.ok(emitter);

        } catch (Exception e) {
            // 2. 만약 실패하면 로그를 남기고, 프론트엔드에게 500 에러 반환
            log.error("SSE 구독 생성 실패 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    //스캔 리스트 제공 api(프런트)
    @GetMapping("/history")
    public ResponseEntity<List<Object>> getScanHistory
    (
            @RequestHeader(value = "guest_uuid", required = true) String guestUuid) {

        // 방어 로직: UUID가 없으면 빈 리스트 반환
        if (guestUuid == null || guestUuid.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        // 서비스 계층 호출해서 Redis 리스트 가져오기
        List<Object> historyList = qrScanRedisService.getHistoryFromRedis(guestUuid);

        return ResponseEntity.ok(historyList);
    }

}