package com.veriq.veriqbe3.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.dto.RedisScanHistoryDto;
import com.veriq.veriqbe3.entity.ScanHistory;
import com.veriq.veriqbe3.repository.ScanHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.DigestUtils;
import org.springframework.transaction.annotation.Transactional;
import com.veriq.veriqbe3.domain.SchemeType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


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
    private final ObjectMapper objectMapper;
    private final ScanHistoryRepository scanHistoryRepository;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

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

                    //  꺼내온 데이터를 다음 요청을 위해 Redis에 캐싱 (Look-Aside)
                    redisTemplate.opsForValue().set(urlKey, objectMapper.writeValueAsString(dbResponse), URL_CACHE_TTL);

                    response = QrScanResponse.builder()
                            .guestUuid(guestUuid).schemeType(result.type()).typeInfo(result.typeInfo())
                            .status(dbResponse.riskLevel())
                            .build();
                }
                else {
                    // 3. DB에도 없음 -> 콜백api호출(분석서버에 요청)
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
    // 스캔 내역 URL 클릭 시 상세 보고서 내려주는 API
    public AnalysisResponse getUrlDetail(String url) throws Exception {
        String cacheKey = buildUrlCacheKey(url);
        String jsonCache = redisTemplate.opsForValue().get(cacheKey);

        if (jsonCache != null) {
            log.info("Redis에서 상세 분석 결과 반환: {}", url);
            return objectMapper.readValue(jsonCache, AnalysisResponse.class);
        }

        //  Redis에 없으면 DB에서 조회
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
    /**

     */
    public void cacheAnalysisResult(String url, AnalysisResponse resultDto) {
        try {
            // 1. 상세 리포트 캐싱 (AnalysisResponse 객체를 통째로 JSON으로 변환하여 저장)
            String urlKey = buildUrlCacheKey(url);
            String jsonCache = objectMapper.writeValueAsString(resultDto);
            redisTemplate.opsForValue().set(urlKey, jsonCache, URL_CACHE_TTL);

            log.info("URL 상세 분석 결과 캐싱 완료: {}", url);

        } catch (Exception e) {
            log.error("Redis 캐싱 실패 - URL: {}", url, e);
        }
    }

    private void saveHistoryToRedis(String guestUuid, QrScanResponse response) {
        // 방어 로직: UUID가 없거나 비어있거나 익명이면, 저장하지 않고 그냥 반환
        if (guestUuid == null || guestUuid.isBlank() || guestUuid.equals("ANONYMOUS")) {
            return;
        }
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
        if (rawUrl == null || rawUrl.isBlank()) {
            return "url_cache:empty";
        }

        // 1. 정규화: 앞뒤 공백 제거
        String cleanUrl = rawUrl.trim();

        // 2. Fragment(#)만 제거하고, QueryString(?)은 반드시 보존!
        // 브라우저 내부용인 # 뒤는 잘라도 되지만, ? 뒤는 데이터이므로 남깁니다.
        int hashIndex = cleanUrl.indexOf('#');
        if (hashIndex != -1) {
            cleanUrl = cleanUrl.substring(0, hashIndex);
        }
        // 3. 해싱: MD5 알고리즘을 사용하여 어떤 길이의 URL이든 짧고 안전한 문자열로 변환
        String hashedUrl = DigestUtils.md5DigestAsHex(cleanUrl.getBytes());

        return "url_cache:" + hashedUrl;
    }
    /**
     * DB 엔티티(ScanHistory)를 Redis용 캐시/응답 DTO(AnalysisResponse)로 변환하는 헬퍼 메서드
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

    @Transactional // DB 저장하다 에러 나면 롤백!,분석서버에서 온 결과 db 및 redis에 저장
    public void saveAndCacheAnalysisResult(AnalysisResponse responseDto, String guestUuid) {

        try {
            // ==========================================
            // 1. 프론트용 DTO를 DB 저장용 Entity로 변환
            // ==========================================
            String url = responseDto.originalUrl(); // 편의를 위해 변수 추출
            String safeGuestUuid = (guestUuid != null && !guestUuid.isBlank()) ? guestUuid : "ANONYMOUS";

            ScanHistory scanHistory = ScanHistory.builder()
                    .guestUuid(safeGuestUuid)
                    .originalUrl(url)
                    .typeInfo(url) // 보통 원본 URL을 넣습니다
                    .schemeType(SchemeType.WEB) // 콜백으로 오는 건 사실상 모두 WEB이라고 가정
                    .analysisTime(responseDto.analysisTime())
                    .totalScore(responseDto.score())
                    .riskLevel(responseDto.riskLevel())

                    // 1. HttpsInfo 빌드
                    .https(ScanHistory.HttpsInfo.builder()
                            .isSecure(responseDto.https() != null && responseDto.https().isSecure())
                            .build())

                    // 2. ShortUrlInfo 빌드
                    .shortUrl(ScanHistory.ShortUrlInfo.builder()
                            .isShortened(responseDto.shortUrl() != null && responseDto.shortUrl().isShortened())
                            .build())

                    // 3. MlInfo 빌드
                    .ml(ScanHistory.MlInfo.builder()
                            .threats(responseDto.ml() != null && responseDto.ml().threats() != null
                                    ? String.join(", ", responseDto.ml().threats()) : null)
                            .mlScore(responseDto.ml() != null ? responseDto.ml().score() : 0)
                            .build())

                    // 4. ExternalApiInfo 빌드
                    .externalApi(ScanHistory.ExternalApiInfo.builder()
                            .apiChecked(responseDto.externalApi() != null && responseDto.externalApi().checked())
                            .apiProvider(responseDto.externalApi() != null ? responseDto.externalApi().provider() : null)
                            .apiResult(responseDto.externalApi() != null ? responseDto.externalApi().result() : null)
                            .build())

                    // 5. InternalDbInfo 빌드
                    .internalDb(ScanHistory.InternalDbInfo.builder()
                            .dbExists(responseDto.internalDb() != null && responseDto.internalDb().exists())
                            .dbReportCount(responseDto.internalDb() != null ? responseDto.internalDb().reportCount() : 0)
                            .dbBlockCount(responseDto.internalDb() != null ? responseDto.internalDb().blockCount() : 0)
                            .build())

                    // 6. RedirectInfo 빌드
                    .redirect(ScanHistory.RedirectInfo.builder()
                            .finalUrl(responseDto.redirect() != null ? responseDto.redirect().finalUrl() : url)
                            .redirectCount(responseDto.redirect() != null ? responseDto.redirect().redirectCount() : 0)
                            .build())

                    // 7. ServerInfo 및 CertificateInfo
                    .serverInfo(ScanHistory.ServerInfo.builder()
                            .serverType(responseDto.serverInfo() != null ? responseDto.serverInfo().type() : null)
                            .serverLocation(responseDto.serverInfo() != null ? responseDto.serverInfo().location() : null)
                            .certificate(responseDto.serverInfo() != null && responseDto.serverInfo().certificate() != null
                                    ? ScanHistory.CertificateInfo.builder()
                                    .certValid(responseDto.serverInfo().certificate().valid())
                                    .certIssuer(responseDto.serverInfo().certificate().issuer())
                                    .certValidFrom(responseDto.serverInfo().certificate().validFrom())
                                    .certValidTo(responseDto.serverInfo().certificate().validTo())
                                    .build()
                                    : null)
                            .build())
                    .build();

            // ==========================================
            // 2. DB에 저장
            // ==========================================
            scanHistoryRepository.save(scanHistory);
            log.info("DB 저장 완료: {}", url);

            // ==========================================
            // 3. Redis 캐시 워밍
            // ==========================================
            cacheAnalysisResult(url, responseDto);

            log.info("DB 저장 완료. 프론트엔드(guest_uuid: {})로 SSE 알림 발송 시도", guestUuid);
            // 프론트엔드로 프로세스 완료 알림
            SseEmitter emitter = emitters.get(guestUuid);
            if (emitter != null) {
                // "COMPLETE"라는 이름표를 붙여서 URL 데이터를 쏴줌
                emitter.send(SseEmitter.event()
                        .name("COMPLETE")
                        .data(responseDto.originalUrl()));
            }

        } catch (Exception e) {
            log.error("DB 저장 및 캐싱 중 에러 발생: {}", responseDto.originalUrl(), e);
            throw new RuntimeException("DB 저장 실패", e);
        }
    }
    // 1. 파이프(진동벨) 만들어주는 메서드
    public SseEmitter createEmitter(String guestUuid) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃
        emitters.put(guestUuid, emitter); // 명부에 등록

        // 파이프 연결이 끊기면 명부에서 삭제
        emitter.onCompletion(() -> emitters.remove(guestUuid));
        emitter.onTimeout(() -> emitters.remove(guestUuid));

        try {
            // 연결되자마자 503 에러 방지용 더미(Dummy) 데이터 하나 전송
            emitter.send(SseEmitter.event().name("INIT").data("Connected!"));
        } catch (Exception e) {
            emitters.remove(guestUuid);
        }
        return emitter;
    }


}
