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

    @Value("${ml.server.secret}") // 파이썬 서버랑 맞춘 비밀키
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
            log.error("[ML 분석 요청 발송 실패] User: {}, URL: {}", guestUuid, url, e);
            throw new RuntimeException("ML 서버 연동 실패");
        }
    }

}
