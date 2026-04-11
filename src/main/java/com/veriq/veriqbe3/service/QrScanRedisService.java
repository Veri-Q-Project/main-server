package com.veriq.veriqbe3.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.dto.RedisScanHistoryDto;
import com.veriq.veriqbe3.entity.ScanHistory;
import com.veriq.veriqbe3.repository.ScanHistoryRepository;
import com.veriq.veriqbe3.dto.RedisUrlCacheDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
@Slf4j
@Service
@RequiredArgsConstructor

public class QrScanRedisService {
    private final QrDecoder qrDecoder;
    private final SchemeClassifier schemeClassifier;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScanHistoryRepository scanHistoryRepository;

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

                AnalysisResponse cacheDto = objectMapper.readValue(cachedJson, AnalysisResponse.class);
                //RedisUrlCacheDto cacheDto = objectMapper.readValue(cachedJson, RedisUrlCacheDto.class);
                // redis 캐시 존재
                response = QrScanResponse.builder()
                        .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                        .status(cacheDto.riskLevel())
                        .build();
            } else {

                Optional<ScanHistory> dbHistory = scanHistoryRepository.findFirstByOriginalUrlOrderByScannedAtDesc(result.typeInfo());
                if (dbHistory.isPresent()) {
                    // DB에 존재 -> 엔티티를 DTO로 변환
                    AnalysisResponse dbResponse = convertToAnalysisResponse(dbHistory.get());

                    // ⭐ 꺼내온 데이터를 다음 요청을 위해 Redis에 캐싱 (Look-Aside)
                    redisTemplate.opsForValue().set(urlKey, objectMapper.writeValueAsString(dbResponse), URL_CACHE_TTL);

                    response = QrScanResponse.builder()
                            .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                            .status(dbResponse.riskLevel())
                            .build();
                }
                else {
                    // 3. DB에도 없음 -> 신규 URL이므로 분석 서버(고근 님)로 비동기 호출
                    // processQrScan.requestAnalysisAsync(result.typeInfo(), guestUuid);

                    response = QrScanResponse.builder()
                            .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                            .status("PROCESSING").build();
                }

            }
        }

        // Redis 스캔 히스토리 저장
        saveHistoryToRedis(guestUuid, response);

        return response;
    }
    // ⭐ 스캔 내역 URL 클릭 시 상세 보고서 내려주는 API
    public AnalysisResponse getUrlDetail(String url) throws Exception {
        String cacheKey = buildUrlCacheKey(url);
        String jsonCache = redisTemplate.opsForValue().get(cacheKey);

        if (jsonCache != null) {
            log.info("Redis에서 상세 분석 결과 반환: {}", url);
            return objectMapper.readValue(jsonCache, AnalysisResponse.class);
        }

        // ⭐ Redis에 없으면 DB에서 조회
        Optional<ScanHistory> dbHistory = scanHistoryRepository.findFirstByOriginalUrlOrderByScannedAtDesc(url);
        if (dbHistory.isEmpty()) {
            return null; // DB에도 없으면 분석이 아직 안 끝났거나 없는 URL
        }

        AnalysisResponse dbResponse = convertToAnalysisResponse(dbHistory.get());

        // 다시 요청될 수 있으니 Redis에 캐싱
        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dbResponse), URL_CACHE_TTL);
        log.info("DB 조회 후 Redis 캐싱 및 반환: {}", url);

        return dbResponse;
    }

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
    /**
     * ⭐ DB 엔티티(ScanHistory)를 Redis용 캐시/응답 DTO(AnalysisResponse)로 변환하는 헬퍼 메서드
     */
    private AnalysisResponse convertToAnalysisResponse(ScanHistory entity) {
        // DB의 threats(String)를 List<String>으로 변환 (콤마 분리 가정)
        java.util.List<String> threatsList = Collections.emptyList();
        if (entity.getMl() != null && entity.getMl().getThreats() != null && !entity.getMl().getThreats().isBlank()) {
            threatsList = Arrays.asList(entity.getMl().getThreats().split(",\\s*"));
        }

        return new AnalysisResponse(
                entity.getAnalysisTime(),
                entity.getOriginalUrl(),
                entity.getHttps() != null ? new AnalysisResponse.HttpsInfo(entity.getHttps().isSecure()) : null,
                entity.getShortUrl() != null ? new AnalysisResponse.ShortUrlInfo(entity.getShortUrl().isShortened()) : null,
                entity.getMl() != null ? new AnalysisResponse.MlInfo(threatsList, entity.getMl().getMlScore()) : null,
                entity.getExternalApi() != null ? new AnalysisResponse.ExternalApiInfo(
                        entity.getExternalApi().isApiChecked(),
                        entity.getExternalApi().getApiProvider(),
                        entity.getExternalApi().getApiResult()
                ) : null,
                entity.getInternalDb() != null ? new AnalysisResponse.InternalDbInfo(
                        entity.getInternalDb().isDbExists(),
                        entity.getInternalDb().getDbReportCount(),
                        entity.getInternalDb().getDbBlockCount()
                ) : null,
                entity.getRedirect() != null ? new AnalysisResponse.RedirectInfo(
                        entity.getRedirect().getFinalUrl(),
                        entity.getRedirect().getRedirectCount()
                ) : null,
                entity.getServerInfo() != null ? new AnalysisResponse.ServerInfo(
                        entity.getServerInfo().getServerType(),
                        entity.getServerInfo().getServerLocation(),
                        entity.getServerInfo().getCertificate() != null ? new AnalysisResponse.CertificateInfo(
                                entity.getServerInfo().getCertificate().isCertValid(),
                                entity.getServerInfo().getCertificate().getCertIssuer(),
                                entity.getServerInfo().getCertificate().getCertValidFrom(),
                                entity.getServerInfo().getCertificate().getCertValidTo()
                        ) : null
                ) : null,
                entity.getTotalScore(),
                entity.getRiskLevel()
        );
    }


}
