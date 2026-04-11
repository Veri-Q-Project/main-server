package com.veriq.veriqgateway.controller;
import com.veriq.veriqgateway.dto.ScanResponse;
import com.veriq.veriqgateway.service.SecurityService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.veriq.veriqgateway.dto.CaptchaRequest;
import org.springframework.beans.factory.annotation.Value;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Profile("be1")
public class GatewayController {
    private final SecurityService securityService;
    private final RestTemplate restTemplate;

    // 🔗 고근 님(BE 3) 서버의 실제 API 주소
    // 실전에서는 localhost 대신 도커 네트워크 서비스 명칭을 쓰기도 하지만,
    // 지금은 로컬 테스트용으로 작성했습니다.
    @Value("${be3.api.url}")
    private  String BE3_URL ;

    /**
     * [POST] /api/v1/scan
     * 1. Redis(도커)를 통한 5회 제한 보안 검사
     * 2. 통과 시 BE 3 서버로 이미지 및 guestId 전송
     */
    /**
     * 클라이언트의 실제 IP를 추출하는 헬퍼 메서드 (도커/프록시 환경 대응)
     */
    private String getClientIp(HttpServletRequest request) {

        return request.getRemoteAddr();
    }
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ScanResponse> processScan(
            @RequestHeader("guest_uuid") String guestUuid,
            @RequestParam("image") MultipartFile image,
            HttpServletRequest request) {

        // --- [STEP 1] 보안 검사 (Redis 연동) ---
        // 도커로 띄운 클라이언트가 보낸 UUID가 아닌, 실제 IP를 식별자로 사용.
        String clientIp = getClientIp(request);
        if (!securityService.isAllowed(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS) // 429 에러
                    .body(ScanResponse.builder()
                            .guestUuid(guestUuid)
                            .status("REJECTED")
                            .error_code("REQUIRE_CAPTCHA")
                            .message("요청 횟수 초과. 안전한 이용을 위해 캡차 인증이 필요합니다.")
                            .build());
        }

        // --- [STEP 2] 데이터 위임 (BE 3로 전송) ---
        // 보안 검사를 통과했다면, 받은 이미지와 guestId를 고근 님 서버로 전달합니다.
        try {
            // 멀티파트 형식의 바구니 생성 (파일 전송용)
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            //Resource imageResource = image.getResource();
            // RestTemplate은 파일명이 없으면 8081에서 "파일이 아니다"라고 판단할 수 있습니다.
            Resource fileResource = new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename(); // 여기서 파일명을 넘겨주는게 핵심!
                }
            };

            body.add("image", fileResource);// 받은 이미지 파일을 그대로 담음
            //body.add("guestUuid", guestUuid);

            // 전송할 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            //headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("guest_uuid", guestUuid); // BE3 서버도 누가 보냈는지 알아야 함

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // RestTemplate을 이용해 서버(BE 3) 호출
            System.out.println(">>> [BE1] BE3(" + BE3_URL + ")로 데이터 전송 시도 중...");
            restTemplate.postForEntity(BE3_URL, requestEntity, String.class);
            System.out.println(">>> [BE1] BE3로부터 응답 무사히 도착!");


            // --- [STEP 3] 최종 성공 응답 ---
            return ResponseEntity.ok(ScanResponse.builder()
                    .guestUuid(guestUuid)
                    .status("PENDING")
                    .message("보안 검사 통과 및 분석 요청이 성공적으로 전달되었습니다.")
                    .build());

        } catch (Exception e) {
            // 고근 님 서버가 꺼져 있거나 통신 에러가 났을 경우 처리
            e.printStackTrace(); // 1. 에러의 원인(Stack Trace)을 콘솔에 강제로 출력합니다.
            System.out.println(">>> 에러 메세지: " + e.getMessage()); // 2. 에러 메세지 요약 출력
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScanResponse.builder()
                            .guestUuid(guestUuid)
                            .status("ERROR")
                            .message("플랫폼 서버(BE 3)와의 연결에 실패했습니다: " + e.getMessage())
                            .build());
        }
    }

    /**
     * [POST] /api/v1/auth/captcha/verify
     * 캡차 성공 시 해당 사용자의 Redis 제한 카운트를 초기화합니다.
     */

    @PostMapping("/auth/captcha/verify")
    public ResponseEntity<ScanResponse> verifyCaptcha(
            @RequestBody CaptchaRequest captchaReq,
            HttpServletRequest request) {

        // [STEP 1] 구글 실전 검증
        String clientIp = getClientIp(request);
        String currentUuid = captchaReq.getGuestUuid();
        boolean isHuman = securityService.verifyWithGoogle(captchaReq.getCaptchaToken());

        if (!isHuman) {
            // 매크로인인 경우
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ScanResponse.builder()
                            .guestUuid(currentUuid)
                            .status("REJECTED")
                            .message("캡차 검증에 실패했습니다. 다시 시도해 주세요.")
                            .build());
        }

        // [STEP 2] 검증 성공 시 Redis 카운트 초기화
        securityService.resetCount(clientIp);

        // [STEP 3] 성공 응답 (프런트가 메시지를 받고 스캔을 재시도함)
        return ResponseEntity.ok(ScanResponse.builder()
                .guestUuid(currentUuid)
                .status("SUCCESS")
                .message("캡차 인증 성공! 이제 다시 스캔을 이용하실 수 있습니다.")
                .build());}
}
