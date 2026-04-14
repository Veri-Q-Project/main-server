package com.veriq.veriqbe3.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        // 1. 타임아웃 설정을 담당하는 공장(Factory) 생성
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 2. 10초 시한폭탄 장착!
        factory.setConnectTimeout(Duration.ofSeconds(10)); // 연결 시도 10초 제한
        factory.setReadTimeout(Duration.ofSeconds(10));    // 응답 대기 10초 제한

        // 3. 설정된 공장을 RestTemplate에 넣어서 완성
        return new RestTemplate(factory);
    }
}

