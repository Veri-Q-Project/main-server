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
import org.springframework.util.DigestUtils;

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
    private static final Duration URL_CACHE_TTL = HISTORY_TTL;

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
            String urlKey =  buildUrlCacheKey(result.typeInfo());
            String cachedJson = redisTemplate.opsForValue().get(urlKey);

            if (cachedJson != null) {

                RedisUrlCacheDto cacheDto = objectMapper.readValue(cachedJson, RedisUrlCacheDto.class);
                // redis 캐시 존재
                response = QrScanResponse.builder()
                        .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                        .status(cacheDto.getResultStatus()).build();
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
            String urlKey = buildUrlCacheKey(url);
            String jsonCache = objectMapper.writeValueAsString(resultDto);
            redisTemplate.opsForValue().set(urlKey, jsonCache, URL_CACHE_TTL);

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
        String cacheKey = buildUrlCacheKey(url);

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
    /**
     * URL 캐시 키를 안전하게 정규화하고 해싱하는 헬퍼 메서드
     */
    private String buildUrlCacheKey(String rawUrl) {
        if (rawUrl == null) {
            return "url_cache:empty";
        }

        // 1. 정규화: 앞뒤 공백 제거 및 소문자 변환
        String cleanUrl = rawUrl.trim().toLowerCase();

        // 2. 쿼리 파라미터(?) 제거 (순수 도메인/경로만 캐싱하여 적중률 향상)
        int questionIndex = cleanUrl.indexOf('?');
        if (questionIndex != -1) {
            cleanUrl = cleanUrl.substring(0, questionIndex);
        }

        // 3. 해싱: MD5 알고리즘을 사용하여 어떤 길이의 URL이든 짧고 안전한 문자열로 변환
        String hashedUrl = DigestUtils.md5DigestAsHex(cleanUrl.getBytes());

        return "url_cache:" + hashedUrl;
    }

}
