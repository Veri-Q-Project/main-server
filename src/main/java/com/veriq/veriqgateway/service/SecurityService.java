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
    @Value("${GOOGLE_RECAPTCHA_SECRET_KEY}")
    private String secretKey;
    @Value("${google.recaptcha.verify-url}")
    private String verifyUrl;

    /**
     * 유저의 요청이 허용 범위 내에 있는지 확인합니다.
     * @param guestUuid 사용자 식별자
     * @return 허용 여부 (true: 통과, false: 캡차 필요)
     */
    public boolean isAllowed(String guestUuid) {
        // Redis에 저장할 키 이름 (예: limit:user-1234)
        String key = "limit:" + guestUuid;

        // Redis에서 현재 카운트 값을 가져옴
        String val = redisTemplate.opsForValue().get(key);
        int count = (val == null) ? 0 : Integer.parseInt(val);

        // 1. 요청 횟수가 제한치(5회)에 도달했는지 확인
        if (count >= LIMIT) {
            return false; // 더 이상 허용하지 않음
        }

        // 2. 카운트 증가 처리
        if (count == 0) {
            // 처음 요청하는 경우, 1을 저장하고 1시간 후 자동 삭제(TTL) 설정
            redisTemplate.opsForValue().set(key, "1", Duration.ofHours(1));
        } else {
            // 이미 기록이 있는 경우 카운트만 1 증가
            redisTemplate.opsForValue().increment(key);
        }

        return true; // 보안 통과
    }

    /**
     * 캡차 인증에 성공했을 때, 해당 유저의 요청 횟수 기록을 지워줍니다.
     */
    public void resetCount(String guestUuid) {
        redisTemplate.delete("limit:" + guestUuid);
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

