package com.veriq.veriqbe3.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
// redis에 안전 url로 저장되어있는 값인지 조회
public class UrlExistSearch {

    private final StringRedisTemplate redisTemplate;

    // BE3 내부에서 Redis를 직접 조회하여 URL 악성 여부 등을 확인합니다. 있으면 true, 없으면 false
    public boolean isUrlExistInRedis(String targetUrl) {

        // Redis에 안전 url을 저장할 때 'url:' 접두어를 붙이도록 함(규칙) / 변경 가능
        String redisKey = "url:" + targetUrl;

        // Redis에 직접 쿼리하여 데이터 존재 여부 확인 (속도가 매우 빠름)
        Boolean hasKey = redisTemplate.hasKey(redisKey);

        return Boolean.TRUE.equals(hasKey);
    }
}

/** BE3 서버의 어딘가 로직...

 public void processScanData(String targetUrl) {
 if (urlExistSearch.isUrlExistInRedis(targetUrl)) {
 안전한 url이므로 client에게 회송...
 // ...
 } else {
 미확인 url이므로 BE2(가흔님)에게 회송...
 // ...
 }
 }
 */