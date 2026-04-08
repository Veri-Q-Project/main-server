package com.veriq.veriqbe3.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.dto.RedisScanHistoryDto;
import com.veriq.veriqbe3.dto.RedisUrlCacheDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
@Slf4j
@Service
@RequiredArgsConstructor

public class QrScanRedisService {
    private final QrDecoder qrDecoder;
    private final SchemeClassifier schemeClassifier;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ⭐ 고근 님이 작업하실 클래스 (나중에 고근님이 구현하시면 주석 해제)
    // private final ProcessQrScan processQrScan;

    private static final int MAX_HISTORY_SIZE = 50;
    private static final Duration HISTORY_TTL = Duration.ofDays(7);

    public QrScanResponse processWithRedis(MultipartFile image, String guestUuid) throws Exception {

        String url = qrDecoder.decode(image);
        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(url);

        QrScanResponse response;

        if (!result.toBe2()) {
            // [비 URL]
            response = QrScanResponse.builder()
                    .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                    .status("COMPLETED").build();
        } else {
            // [URL] Redis 캐시 확인
            String urlKey = "url_cache:" + result.typeInfo();
            String cachedResult = redisTemplate.opsForValue().get(urlKey);

            if (cachedResult != null) {
                // redis 캐시 존재
                response = QrScanResponse.builder()
                        .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                        .status(cachedResult).build();
            } else {
                // redis 캐시 없음(비동기 호출)
                // processQrScan.requestAnalysisAsync(result.typeInfo(), guestUuid);

                response = QrScanResponse.builder()
                        .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                        .status("PROCESSING").build();
            }
        }

        // Redis 스캔 히스토리 저장
        saveHistoryToRedis(guestUuid, response);

        return response;
    }
    /**
     * 고근 님의 콜백 API에서 분석이 끝난 후 호출할 메서드
     */
    public void cacheAnalysisResult(String url, RedisUrlCacheDto resultDto) {
        try {
            // 1. 상세 리포트 캐싱 (String 타입으로 1:1 저장)
            String urlKey = "url_cache:" + url;
            String jsonCache = objectMapper.writeValueAsString(resultDto);
            redisTemplate.opsForValue().set(urlKey, jsonCache, Duration.ofHours(24));

            log.info("URL 상세 분석 결과 캐싱 완료: {}", url);

        } catch (Exception e) {
            log.error("Redis 캐싱 실패 - URL: {}", url, e);
        }
    }

    private void saveHistoryToRedis(String guestUuid, QrScanResponse response) {
        try {
            String historyKey = "history:" + guestUuid;
            RedisScanHistoryDto item = RedisScanHistoryDto.from(response);
            String jsonItem = objectMapper.writeValueAsString(item);

            redisTemplate.opsForList().leftPush(historyKey, jsonItem);
            // 항상 50개만 남기고 옛날 데이터는 잘라버림
            redisTemplate.opsForList().trim(historyKey, 0, MAX_HISTORY_SIZE - 1);
            redisTemplate.expire(historyKey, HISTORY_TTL);
        } catch (Exception e) {
            log.error("Redis 히스토리 저장 실패 - guestUuid: {}", guestUuid, e);
        }

}   //스캔 내역 url클릭시 다시 프론트로 url에 상세보고서 보내는 api
    public RedisUrlCacheDto getUrlDetail(String url) throws Exception {
        // 1. Redis 키 생성
        String cacheKey = "url_cache:" + url;

        // 2. Redis에서 데이터 꺼내기
        String jsonCache = redisTemplate.opsForValue().get(cacheKey);

        if (jsonCache == null) {
            // 캐시가 비어있다면? (만료되었거나 처음 보는 URL인 경우)
            // 이때는 고근 님께 다시 분석 요청을 하거나, 에러를 던져야 합니다.
            return null;
        }

        // 3. JSON 문자열을 다시 자바 객체(RedisUrlCacheDto)로 변환해서 반환
        return objectMapper.readValue(jsonCache, RedisUrlCacheDto.class);
    }

}
