package com.veriq.veriqbe3.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
@Slf4j
@Service
@RequiredArgsConstructor
public class MlRequestService {
    @Value("${ml.api.url}") // application.yml에 정의된 파이썬 서버 URL (예: http://.../analyze)
    private String mlServerUrl;

    @Value("${ML_SECRET_KEY}")// 파이썬 서버랑 맞춘 비밀키
    private String mlServerSecret;

    private final RestTemplate restTemplate;

    public void sendToPythonServer(String guestUuid, String url) {
        try {
            // 1. 헤더 세팅
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-ML-Secret", mlServerSecret);
            headers.set("guest_uuid", guestUuid);       // 파이썬이 나중에 콜백할 때 이 UUID를 그대로 돌려줌!

            // 2. 바디 세팅 (파이썬 서버가 요구하는 JSON 규격에 맞춤) 원본 url들어감
            Map<String, String> body = new HashMap<>();
            body.put("url", url);//url:"https://naver.com" 이 json형태로 들어감
            body.put("guestUuid", guestUuid);
            // 3. 엔티티 조립 바디랑 헤더 조립
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            // 4. 파이썬 서버로 POST 요청 발사

            restTemplate.postForObject(mlServerUrl, requestEntity, String.class);
            //로그에 guestuuid 정보와 url정보는 일부만 남긴다

            String maskedUuid = (guestUuid != null && guestUuid.length() > 8)
                    ? guestUuid.substring(0, 8) + "****"
                    : "UNKNOWN";

            String safeHost = "UNKNOWN_HOST";
            try {
                java.net.URI uri = new java.net.URI(url);
                safeHost = uri.getHost(); // https://naver.com/token=123 -> naver.com 만 추출
            } catch (Exception ignored) {
                // URL 형식이 아닐 경우 무시
            }

            log.info("[ML 분석 요청 발송 성공] User: {}, Host: {}", maskedUuid, safeHost);

        } catch (Exception e) {
            //  [STEP 1] 실패 로그용 마스킹 처리 (성공 로직과 동일하게!)
            String maskedUuid = (guestUuid != null && guestUuid.length() > 8)
                    ? guestUuid.substring(0, 8) + "****"
                    : "UNKNOWN";

            String safeHost = "UNKNOWN_HOST";
            try {
                java.net.URI uri = new java.net.URI(url);
                safeHost = uri.getHost(); // 민감한 파라미터를 제외한 도메인만 추출
            } catch (Exception ignored) { }

            //  [STEP 2] 마스킹된 정보로 에러 로그 출력
            // 마지막에 e를 넣어줘야 어떤 통신 에러가 났는지 스택 트레이스가 남습니다.
            log.error("[ML 분석 요청 발송 실패] User: {}, Host: {}", maskedUuid, safeHost, e);

            //  [STEP 3] 원본 예외(e)를 포함해서 던지기 (상위 컨트롤러의 502 처리를 위해!)
            // e를 인자로 넘겨야 'Root Cause'가 보존되어 나중에 추적이 가능합니다.
            throw new RuntimeException("ML 서버 연동 실패", e);
        }
    }

}
