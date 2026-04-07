package com.veriq.veriqgateway.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.veriq.veriqgateway.dto.GoogleCaptchaResponse;
@Service
@RequiredArgsConstructor

public class SecurityService {
    // Redis 조작을 위한 스프링 표준 템플릿
    private final StringRedisTemplate redisTemplate;

    // 사용자(Guest)에게 허용할 최대 스캔 횟수
    private static final int LIMIT = 10;
    private final RestTemplate restTemplate; // 메인 클래스에서 등록한 빈 사용
    @Value("${google.recaptcha.secret-key}")
    private String secretKey;
    @Value("${google.recaptcha.verify-url}")
    private String verifyUrl;

    /**
     * 유저의 요청이 허용 범위 내에 있는지 확인합니다.
     * @param clinetIp 사용자 식별자
     * @return 허용 여부 (true: 통과, false: 캡차 필요)
     */
    public boolean isAllowed(String clinetIp) {
        // Redis에 저장할 키 이름 (limit+ip)
        String key = "limit:" + clinetIp;
        // 1. [원자적 연산] 읽기 + 비교하기 전에 무조건 1을 먼저 증가시킵니다.
        // Redis는 자체적으로 동시성을 제어하므로, 이 연산은 절대 꼬이지 않습니다.
        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount == null) {
            return false; // Redis 에러 대비 안전 장치
        }

        // 2. 만약 방금 증가시킨 결과가 1이라면 (최초 요청이라면)
        if (currentCount == 1L) {
            // 1시간 뒤에 기록이 사라지도록 타이머(TTL)를 설정합니다.
            redisTemplate.expire(key, Duration.ofHours(1));
        }

        // 3. 만약 방금 올린 숫자가 제한선(LIMIT)을 넘어버렸다면?
        if (currentCount > LIMIT) {
            // 이번 요청은 차단할 것이므로, 방금 올렸던 카운트를 다시 빼서 원상복구(Rollback) 해줍니다.
            redisTemplate.opsForValue().decrement(key);
            return false; //  차단
        }

        // 4. 10회 이하라면 무사히 통과
        return true; //  통과

    }

    /**
     * 캡차 인증에 성공했을 때, 해당 유저의 요청 횟수 기록을 지워줍니다.
     */
    public void resetCount(String clinetIp) {
        redisTemplate.delete("limit:" + clinetIp);
    }
    public boolean verifyWithGoogle(String captchaToken) {
        // 구글 API는 폼 데이터 형식을 원합니다.
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("secret", secretKey);
        params.add("response", captchaToken);

        try {
            // 구글에 "이 토큰 진짜야?"라고 물어봄
            GoogleCaptchaResponse response = restTemplate.postForObject(verifyUrl, params, GoogleCaptchaResponse.class);
            return response != null && response.isSuccess();
        } catch (Exception e) {
            System.err.println("구글 캡차 통신 에러: " + e.getMessage());
            return false;
        }
    }
}

