package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.ProgressRequest;
import com.veriq.veriqbe3.dto.RedisScanHistoryDto;
import com.veriq.veriqbe3.service.QrScanRedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

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
    @Operation(
            summary = "QR 코드 업로드 및 분석 시작",
            description = """
        ###  로딩 UI 및 SSE 연결 가이드
        
        이 API의 응답 결과(`status`)에 따라 프론트엔드 흐름을 제어해야 합니다.
        
        **1. 공통 사항**
        - 응답을 받으면 즉시 "QR 코드 디코딩이 완료되었습니다" 메시지와 함께 로딩 UI를 준비합니다.
        
        **2. 상태(`status`)별 분기 처리**
        
        - 🟢 **`status == "COMPLETED"` (기존 데이터 존재 또는 비 URL)**
            - **SSE 연결을 하지 마세요.**
            - **`isUrl == false`**: 비 URL 전용 로딩창을 띄우고 빠르게 로딩창을 순차 진행(스킵 타입 반영) 후 완료 처리합니다.
            - **`isUrl == true`**: 기존 DB/캐시 적중 사례입니다. URL 전용 로딩창을 빠르게 실행하고, 응답에 포함된 `originalUrl`을 사용하여 `/detail` API를 즉시 호출합니다.
        
        - 🟡 **`status == "PROCESSING"` (신규 분석 필요)**
            - **반드시 `/subscribe` 엔드포인트로 SSE 파이프를 연결해야 합니다.**
            - SSE로 전달되는 `step`, `status` 정보를 실시간으로 로딩 UI에 바인딩하세요.
            
        **3. 스킴 타입(`schemeType`) 처리**
        - `PROCESSING` 상태일 때는 값이 들어와도 **무시**합니다.
        - `COMPLETED` 상태가 되었을 때:
            - `isUrl == true`이면 **무시**합니다.
            - `isUrl == false`이면 해당 값(예: `tel`, `smsto`)을 UI에 **반영**합니다.
        """
    )
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) //multipart_form_data 제한
    public ResponseEntity<?> uploadQrImage(
            @RequestHeader(value = "guest_uuid" ) String guestUuid,
            @RequestParam("image") MultipartFile image) {
        try {
            // 🚨 2. 서비스가 주는 결과물을 Object로 받습니다!
            Object response = qrScanRedisService.processWithRedis(image, guestUuid);

            // 🚨 3. 결과물이 어떤 타입인지(완성본인지, 로딩표인지)에 따라 응답을 다르게 줍니다!
            if (response instanceof AnalysisResponse) {
                // [Fast-Path] 캐시/DB에 있던 신선한 상세 데이터인 경우 (200 OK)
                log.info("✅ 신선한 데이터 적중! 분석 결과를 즉시 반환합니다.");
                return ResponseEntity.ok(response);
            } else {
                // [ML-Trigger] 파이썬 분석이 시작되어 진행 상태(QrScanResponse)를 받은 경우 (202 Accepted)
                log.info("🔄 파이썬 서버로 분석을 지시했습니다. 프론트엔드에 대기 신호를 보냅니다.");
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
            }

            // --- 아래 예외 처리는 상민님 코드 그대로 유지 ---
        } catch (IllegalArgumentException e) { // 사용자 잘못 -> 400
            log.warn("잘못된 QR 업로드 요청 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.badRequest().build();

        } catch (org.springframework.web.client.RestClientException e) { // 파이썬 통신 에러 -> 502
            log.error("QR 스캔 요청 처리 중 서버(다운스트림) 에러 발생 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();

        } catch (Exception e) { // 스프링 서버 내부 에러 -> 500
            log.error("QR 스캔 요청 처리 중 서버 내부 에러 발생 - guestUuid: {}", guestUuid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    //  스캔 내역 및 로딩화면 후 상세 보고서 API 엔드포인트 ,프런트랑 연결
    @Operation(
            summary = "분석 결과 상세 조회",
            description = """
    ### 호출 시점:
    SSE의 `COMPLETE` 이벤트를 수신한 직후 호출합니다.
    
    ### 요청 방법:
    - **URL 파라미터**: `COMPLETE` 이벤트 메시지에 포함된 `originalUrl`을 `url` 쿼리 파라미터로 전달합니다.
    - **헤더**: 요청 시 `guest_uuid`를 반드시 포함해야 합니다.
    """
    )
    @GetMapping("/detail")
    public ResponseEntity<AnalysisResponse> getUrlDetail(@RequestParam("url") String url) {
        try {
            log.info("상세 분석 결과 요청 들어옴 - URL: {}", url);

            // Look-Aside 캐시 로직 호출
            AnalysisResponse response = qrScanRedisService.getUrlDetail(url);

            if (response == null) {
                // 캐시에도 없고 DB에도 없는 경우
                log.warn("URL 분석 결과를 찾을 수 없습니다: {}", url);
                // ❌ 기존: .body("문자열") -> ✅ 수정: .build()로 본문 없이 404 상태코드만 반환
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // 성공 시 200 OK와 함께 데이터 쫙 뿌려주기
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("상세 조회 중 서버 에러 발생 - URL: {}", url, e);
            // ❌ 기존: .body("문자열") -> ✅ 수정: .build()로 본문 없이 500 상태코드만 반환
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }




    // ML 서버가 분석을 마치고 분석최종결과를 던져주는 콜백 API

    @PostMapping("/callback")
    public ResponseEntity<String> analysisCallback(
            @RequestHeader(value = "guest_uuid", required = false) String guestUuid,
            @RequestHeader(value = "X-ML-Secret", required = false) String providedSecret,
            @RequestBody AnalysisResponse resultDto) {

        try {
            // 🚨 1. 가장 먼저 UUID부터 검사합니다! (입구 컷)
            if (guestUuid == null || guestUuid.isBlank()) {
                log.error("❌ [콜백 실패] 파이썬이 헤더에 guest_uuid를 안 보냈습니다! URL: {}", resultDto.originalUrl());
                // UUID가 없으면 DB 저장도, SSE 통신도 할 필요 없이 바로 반송!
                return ResponseEntity.badRequest().body("guest_uuid is missing");
            }
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
    @Operation(
            summary = "실시간 분석 상태 구독 (SSE)",
            description = """
    ###  주의사항: 분석 요청(Upload) 전 반드시 이 파이프라인이 먼저 연결되어야 합니다.
    연결되지 않은 상태에서 분석 요청 시, 중간 과정을 수신할 수 없습니다.
    
    **수신 이벤트 채널 안내:**
    - `INIT`: SSE 연결 성공 시 전송
    - `progress`: 분석 단계별 상태 전송 (Step, Status 포함)
    - `COMPLETE`: 분석 최종 완료 시 전송 (**이벤트 데이터에 포함된 URL로 상세조회 수행**)
    """
    )
    @GetMapping(value = "/subscribe", produces = "text/event-stream;charset=UTF-8")
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
    @Operation(summary = "스캔 리스트 제공 API", description = "사용자의 최근 QR 스캔 이력을 반환합니다.")
    @GetMapping("/history")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "성공적으로 이력을 조회함",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = RedisScanHistoryDto.class))
                    )
            )
    })
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