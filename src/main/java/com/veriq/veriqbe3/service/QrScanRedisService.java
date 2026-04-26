package com.veriq.veriqbe3.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.veriq.veriqbe3.domain.RiskLevel;
import com.veriq.veriqbe3.dto.AnalysisResponse;
import com.veriq.veriqbe3.dto.ProgressRequest;
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


import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor

public class QrScanRedisService {
    private final QrDecoder qrDecoder;
    private final SchemeClassifier schemeClassifier;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ScanHistoryRepository scanHistoryRepository;
    private final MlRequestService mlRequestService;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();




    private static final int MAX_HISTORY_SIZE = 50;
    private static final Duration HISTORY_TTL = Duration.ofDays(7);


    public Object processWithRedis(MultipartFile image, String guestUuid) throws Exception {

        String url = qrDecoder.decode(image);
        // 2. 디코딩 성공 시, 프론트로 실시간 텍스트만 쏘기!
        //  [방어 로직] 디코딩 실패 시 (QR이 아니거나 깨진 이미지) 즉시 에러 던지기
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("QR 코드 디코딩에 실패했습니다.");
        }

        // 2. URL 분류
        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(url);

        // 3. 프론트로 실시간 텍스트 쏘기 (삼항 연산자로 깔끔하게 정리)
        // 1. URL 여부와 메시지를 명확한 변수로 빼둡니다.
        boolean isUrl = result.toBe2();
        String decodingMsg = isUrl
                ? "QR 코드 디코딩이 완료되었습니다."
                : "QR 코드 내용을 확인한 결과 URL 형식이 아니었습니다.";


        log.info("[Decode] {}", decodingMsg);

// 2. [비 URL] 처리 로직
        if (!isUrl) {
            return QrScanResponse.builder()
                    .guestUuid(guestUuid)
                    .typeInfo(result.typeInfo())
                    .status("COMPLETED")
                    .isUrl(false)
                    .message(decodingMsg)
                    .build();
        }

// 3. [URL] 처리 로직
        // 4-1. 캐시 및 DB에서 '신선한' 데이터 가져오기 시도
        AnalysisResponse freshResult = getUrlDetail(result.typeInfo());

        if (freshResult != null) {
            // 🚨 [핵심 A] 신선한 데이터가 있으면 파이썬 분석 스킵! AnalysisResponse 객체를 통째로 리턴!

            log.info("[Fast-Path] 신선한 캐시/DB 데이터 적중! 파이썬 분석 생략 - URL: {}", result.typeInfo());
            saveHitHistory(freshResult, guestUuid);
            return freshResult;
        }

        // 4-2. 데이터가 아예 없거나 너무 낡았다면? (null이 반환된 경우)
        try {
            log.info("데이터가 없거나 오래되었습니다. 파이썬 ML 서버에 분석을 지시합니다. URL: {}", result.typeInfo());

            // 여기서 파이썬 서버로 분석 지시 핑(Ping)을 날립니다.
            mlRequestService.sendToPythonServer(guestUuid, result.typeInfo());

            // 🚨 [핵심 B] 파이썬이 일하러 갔으니, 프론트에게는 QrScanResponse(로딩 상태)를 리턴!
            return QrScanResponse.builder()
                    .guestUuid(guestUuid)
                    .typeInfo(result.typeInfo())
                    .status("PROCESSING") // 프론트는 이걸 보고 SSE 대기 화면을 띄웁니다.
                    .isUrl(true)
                    .message("분석이 시작되었습니다.")
                    .build();

        } catch (org.springframework.web.client.RestClientException e) {
            log.error("ML 서버 통신 실패 (RestClientException) - guestUuid: {}", guestUuid, e);
            throw e; // 컨트롤러가 잡아서 502 Bad Gateway로 처리

        } catch (Exception e) {
            log.error("분석 요청 중 알 수 없는 에러", e);
            throw new RuntimeException("ML 서버 통신 실패", e);
        }


    }
    /**
     * [Fast-Path 전용] 캐시/DB 적중 시, 분석 없이 '방문 기록'만 새로 남기는 메서드
     */
    public void saveHitHistory(AnalysisResponse responseDto, String guestUuid) {
        try {
            String url = responseDto.originalUrl();
            String safeGuestUuid = (guestUuid != null && !guestUuid.isBlank()) ? guestUuid : "ANONYMOUS";

            // 기존 분석 결과(responseDto)를 복사하되, '새로운 엔티티'를 만듭니다.
            ScanHistory hitHistory = ScanHistory.builder()
                    .guestUuid(safeGuestUuid)
                    .originalUrl(url)
                    .typeInfo(url)
                    .schemeType(SchemeType.WEB)

                    // 🚨 핵심 1: ML 분석 시간은 기존 캐시에 있던 '과거 시간'을 그대로 유지!
                    .analysisTime(responseDto.analysisTime())

                    // 🚨 핵심 2: null 방어 로직 (이전에 수정한 것과 동일하게 적용)
                    .totalScore(responseDto.score() != null ? responseDto.score() : 0)
                    .riskLevel(responseDto.riskLevel() != null ? responseDto.riskLevel().name() : "SUSPICIOUS")

                    // 1. HttpsInfo
                    .https(ScanHistory.HttpsInfo.builder()
                            .isSecure(responseDto.https() != null && responseDto.https().isSecure())
                            .build())

                    // 2. ShortUrlInfo
                    .shortUrl(ScanHistory.ShortUrlInfo.builder()
                            .isShortened(responseDto.shortUrl() != null && responseDto.shortUrl().isShortened())
                            .build())

                    // 3. MlInfo (null 방어)
                    .ml(ScanHistory.MlInfo.builder()
                            .threats(responseDto.ml() != null && responseDto.ml().threats() != null
                                    ? String.join(", ", responseDto.ml().threats()) : null)
                            .mlScore(responseDto.ml() != null && responseDto.ml().score() != null ? responseDto.ml().score() : 0)
                            .build())

                    // 4. ExternalApiInfo
                    .externalApi(ScanHistory.ExternalApiInfo.builder()
                            .apiChecked(responseDto.externalApi() != null && responseDto.externalApi().checked())
                            .apiProvider(responseDto.externalApi() != null ? responseDto.externalApi().provider() : null)
                            .apiResult(responseDto.externalApi() != null ? responseDto.externalApi().result() : null)
                            .build())

                    // 5. InternalDbInfo
                    .internalDb(ScanHistory.InternalDbInfo.builder()
                            .dbExists(responseDto.internalDb() != null && responseDto.internalDb().exists())
                            .dbReportCount(responseDto.internalDb() != null && responseDto.internalDb().reportCount() != null ? responseDto.internalDb().reportCount() : 0)
                            .dbBlockCount(responseDto.internalDb() != null && responseDto.internalDb().blockCount() != null ? responseDto.internalDb().blockCount() : 0)
                            .build())

                    // 6. RedirectInfo (null 방어)
                    .redirect(ScanHistory.RedirectInfo.builder()
                            .finalUrl(responseDto.redirect() != null ? responseDto.redirect().finalUrl() : url)
                            .redirectCount(responseDto.redirect() != null && responseDto.redirect().redirectCount() != null ? responseDto.redirect().redirectCount() : 0)
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

            // 🚨 핵심 3: 새로운 엔티티로 DB에 Insert!
            // 여기서 @PrePersist가 발동해서 hitHistory.scannedAt 에 '현재 스캔 시간'이 자동으로 들어갑니다!
            scanHistoryRepository.save(hitHistory);

            log.info("캐시 적중 - 새로운 스캔 히스토리(방문 기록) DB 저장 완료: {}", url);
            //  유저 개인의 Redis 히스토리 리스트에도 추가
            saveHistoryToRedis(guestUuid, responseDto);

        } catch (Exception e) {
            log.error("캐시 적중 히스토리 저장 중 에러 발생", e);
        }
    }
    // 스캔 내역 URL 클릭 시 상세 보고서 내려주는 API
    public AnalysisResponse getUrlDetail(String url) throws Exception {
        String cacheKey = buildUrlCacheKey(url);
        String jsonCache = redisTemplate.opsForValue().get(cacheKey);
        //상세보고서 요청시  redis에서 분석 결과 꺼내오는 과정
        //캐시값이 꺠졌을떄 대비해서 try catch문 추가

        if (jsonCache != null) {
            try {
                log.info("Redis 캐시 적중! 상세 분석 결과 반환: {}", url);
                return objectMapper.readValue(jsonCache, AnalysisResponse.class);
            } catch (Exception e) {
                // 캐시 데이터가 깨졌을 경우 처리
                log.error("Redis 캐시 파싱 실패 (손상된 데이터). 캐시 삭제 후 DB 조회를 시도합니다. URL: {}", url, e);
                redisTemplate.delete(cacheKey); // 깨진 데이터 삭제
                // 여기서 return하지 않고 그대로 두면 아래의 DB 조회 로직으로 자연스럽게 넘어감
            }
        }
        //  Redis에 없으면 DB에서 조회
        //스캔한 내역중 최신 내역을 db에서 뽑아옴
        Optional<ScanHistory> dbHistory = scanHistoryRepository.findFirstByOriginalUrlOrderByScannedAtDesc(url);
        if (dbHistory.isEmpty()) {
            return null; // DB에도 없으면 분석이 아직 안 끝났거나 없는 URL
        }
        // 🚨 여기서 history 변수를 명확하게 뽑아냅니다! (에러 해결 핵심)
        ScanHistory history = dbHistory.get();


        // 3. 신선도 체크
        if (isStale(history)) {
            return null; // 상했으면 null (processWithRedis에서 캐치해서 파이썬 호출함)
        }

        // 4. 신선하면 DTO로 변환 후 캐싱 및 반환
        AnalysisResponse dbResponse = convertToAnalysisResponse(history);
        Duration dynamicTtl = calculateDynamicTtl(RiskLevel.from(history.getRiskLevel()));
        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dbResponse), dynamicTtl);
        log.info("DB 조회 결과가 신선하여 Redis 캐싱 후 반환 (TTL: {}일) : {}", dynamicTtl.toDays(), url);
        return dbResponse;
    }
    /**
     * 데이터가 유효 기간을 넘었는지 확인//db 데이터 갱신(분석시간 위주)
     */
    private boolean isStale(ScanHistory history) {
        if (history.getAnalysisTime() == null) return true;

        long daysPassed = java.time.Duration.between(history.getAnalysisTime(), java.time.LocalDateTime.now()).toDays();
        // SAFE는 1일, 나머지는 3일을 임계치로 설정
        long threshold = "SAFE".equalsIgnoreCase(history.getRiskLevel()) ? 1 : 3;

        return daysPassed >= threshold;
    }
    /**

     */
    public void cacheAnalysisResult(String url, AnalysisResponse resultDto,Duration ttl) {
        try {
            // 1. 상세 리포트 캐싱 (AnalysisResponse 객체를 통째로 JSON으로 변환하여 저장)
            String urlKey = buildUrlCacheKey(url);
            String jsonCache = objectMapper.writeValueAsString(resultDto);
            // 🚨 핵심: 고정된 URL_CACHE_TTL 대신, 밖에서 받아온 동적 ttl을 꽂아줍니다!
            redisTemplate.opsForValue().set(urlKey, jsonCache, ttl);

            log.info("URL 상세 분석 결과 캐싱 완료: {} (적용된 TTL: {}일)", url, ttl.toDays());

        } catch (Exception e) {
            log.error("Redis 캐싱 실패 - URL: {}", url, e);
        }
    }

    public void saveHistoryToRedis(String guestUuid, AnalysisResponse mlResponse) {
        // 1. 방어 로직: 저장할 필요가 없는 경우 즉시 반환
        if (guestUuid == null || guestUuid.isBlank() || guestUuid.equals("ANONYMOUS")) {
            return;
        }

        try {
            String historyKey = "history:" + guestUuid;
            RedisScanHistoryDto newItem = RedisScanHistoryDto.from(mlResponse);

            // 2. 기존 히스토리 전체 조회 (0부터 끝까지)
            List<String> rawHistory = redisTemplate.opsForList().range(historyKey, 0, -1);
            List<RedisScanHistoryDto> currentHistory = new ArrayList<>();

            if (rawHistory != null) {
                for (String json : rawHistory) {
                    currentHistory.add(objectMapper.readValue(json, RedisScanHistoryDto.class));
                }

                // 3. 중복 제거: 동일한 URL(typeInfo)이 있다면 리스트에서 삭제
                currentHistory.removeIf(oldItem ->
                        oldItem.getTypeInfo().equals(newItem.getTypeInfo())
                );
            }

            // 4. Redis 데이터 갱신 (전체 삭제 후 정제된 데이터 재삽입)
            redisTemplate.delete(historyKey);

            // 새 항목(최신)을 리스트에 추가
            String jsonNewItem = objectMapper.writeValueAsString(newItem);
            redisTemplate.opsForList().leftPush(historyKey, jsonNewItem);

            // 기존 데이터들 중복 제거된 상태로 다시 추가
            if (!currentHistory.isEmpty()) {
                for (RedisScanHistoryDto oldItem : currentHistory) {
                    redisTemplate.opsForList().rightPush(historyKey, objectMapper.writeValueAsString(oldItem));
                }
            }

            // 5. 최신 데이터 50개 유지 및 유효기간 설정
            redisTemplate.opsForList().trim(historyKey, 0, MAX_HISTORY_SIZE - 1);
            redisTemplate.expire(historyKey, HISTORY_TTL);

        } catch (Exception e) {
            log.error("Redis 히스토리 저장 중 중복 제거 실패 - guestUuid: {}", guestUuid, e);
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
                RiskLevel.from(entity.getRiskLevel())
        );
    }
    /**
     * [추가된 핵심 로직] 위험도에 따른 차등 TTL 계산 메서드//redis갱신
     */
    private Duration calculateDynamicTtl(RiskLevel riskLevel) {
        if (riskLevel == null) return Duration.ofDays(2); // 안전장치 (기본값)

        return switch (riskLevel) {
            case SAFE -> Duration.ofDays(1);
            case SUSPICIOUS, DANGER -> Duration.ofDays(3);
        };
    }

    @Transactional
    // DB 저장하다 에러 나면 롤백!,
    // 분석서버에서 온 결과 db 및 redis에 저장
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
                    .totalScore(responseDto.score() != null ? responseDto.score() : 0)
                    .riskLevel(responseDto.riskLevel() != null ? responseDto.riskLevel().name() : "SUSPICIOUS")

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
                            // 🚨 2. mlScore 방어 (null이 아닐 때만 값을 넣고, null이면 0)
                            .mlScore(responseDto.ml() != null && responseDto.ml().score() != null ? responseDto.ml().score() : 0)
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
                            .dbReportCount(responseDto.internalDb() != null && responseDto.internalDb().reportCount() != null ? responseDto.internalDb().reportCount() : 0)
                            .dbBlockCount(responseDto.internalDb() != null && responseDto.internalDb().blockCount() != null ? responseDto.internalDb().blockCount() : 0)
                            .build())


                    // 6. RedirectInfo 빌드
                    .redirect(ScanHistory.RedirectInfo.builder()
                            .finalUrl(responseDto.redirect() != null ? responseDto.redirect().finalUrl() : url)
                            // 🚨 3. redirectCount 방어 (null이 아닐 때만 값을 넣고, null이면 0)
                            .redirectCount(responseDto.redirect() != null && responseDto.redirect().redirectCount() != null ? responseDto.redirect().redirectCount() : 0)
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
            // 3. 차등 TTL 적용하여 Redis 캐시 워밍 (수정된 부분!)
            // ==========================================
            Duration dynamicTtl = calculateDynamicTtl(responseDto.riskLevel());
            cacheAnalysisResult(url, responseDto, dynamicTtl);
            //  분석 완료 후 유저 개인의 Redis 히스토리 리스트에도 추가!
            saveHistoryToRedis(guestUuid, responseDto);

            log.info("모든 저장 및 동적 캐싱 프로세스 완료");
            // 프론트엔드로 프로세스 완료 알림 진행...




        } catch (Exception e) {
            log.error("DB 저장 및 캐싱 중 에러 발생: {}", responseDto.originalUrl(), e);
            throw new RuntimeException("DB 저장 실패", e);
        }
    }
    // 1. 파이프(진동벨) 만들어주는 메서드
    public SseEmitter createEmitter(String guestUuid) {
        SseEmitter emitter = new SseEmitter(300000L); // 5분 타임아웃
        emitters.put(guestUuid, emitter); // 명부에 등록
        //3. 연결되자마자 프론트엔드에게 환영 인사(Init) 보내기!
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT") // 채널 이름을 INIT으로 설정
                    .data("SSE 파이프라인 연결 성공! 분석 대기 중..."));

            log.info(" [SSE] 유저와 파이프라인 연결 성공: guestUuid = {}", guestUuid);
        } catch (IOException e) {
            emitters.remove(guestUuid);
            log.error(" SSE 초기 메시지 전송 실패", e);
        }

        // 파이프 연결이 끊기면 명부에서 삭제
        emitter.onCompletion(() -> emitters.remove(guestUuid, emitter));
        emitter.onTimeout(() -> emitters.remove(guestUuid, emitter));

        return emitter;
    }
    /**
     * 특정 guestUuid를 가진 프론트엔드 파이프로 메시지를 전송합니다.
     */
    public void sendSseEvent(String guestUuid, String message) {
        // 1. Map에 저장해둔 해당 유저의 파이프(SseEmitter)를 꺼냅니다.

        SseEmitter emitter = emitters.get(guestUuid);

        if (emitter != null) {
            try {
                // 2. 파이프가 존재하면 프론트로 메시지를 전송
                emitter.send(SseEmitter.event()
                        .name("progress") // 프론트엔드에서 이 이름으로 이벤트를 수신할 수 있습니다.
                        .data(message));  // 실제 전송할 텍스트 ("도메인 사칭 분석 완료" 등)

            } catch (Exception e) {
                // 3. 만약 쏘는 중에 에러가 났다? (프론트가 새로고침해서 파이프가 끊긴 경우 등)
                // 그러면 죽은 파이프는 미련 없이 버립니다.
                emitters.remove(guestUuid);
                log.error(" SSE 파이프 전송 실패 및 삭제: guestUuid = {}", guestUuid, e);
            }
        } else {
            // 파이프가 아예 없는 경우 (이미 만료되었거나 연결된 적 없는 경우)
            log.warn("⚠전송할 SSE 파이프를 찾을 수 없습니다: guestUuid = {}", guestUuid);
        }
    }
    // 🏁 최종완료용 (채널 이름: COMPLETE)
    public void sendSseCompleteEvent(String guestUuid, String url) {
        SseEmitter emitter = emitters.get(guestUuid);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("COMPLETE")
                        .data(url));

                // 완료 신호를 보낸 후에는 파이프를 깔끔하게 닫아주는 게 좋습니다.
                emitter.complete();
                emitters.remove(guestUuid);
            } catch (Exception e) {
                //  추가: 누가 끊겼는지, 왜 끊겼는지 경고 로그를 남겨줍니다.
                log.warn("SSE COMPLETE 이벤트 전송 실패 - guestUuid: {}", guestUuid, e);
                emitters.remove(guestUuid);
            }
        }

    }
    // 저장된 히스토리 목록 가져오기
    public List<Object> getHistoryFromRedis(String guestUuid) {
        String historyKey = "history:" + guestUuid;
        try {
            // 0부터 -1은 리스트의 '처음부터 끝까지'를 의미합니다.
            List<String> jsonHistoryList = redisTemplate.opsForList().range(historyKey, 0, -1);

            if (jsonHistoryList == null || jsonHistoryList.isEmpty()) {
                return List.of();
            }

            // Redis에 저장된 String(JSON)을 다시 Object(Map)로 변환해서 리턴해야
            // 프론트엔드에서 깔끔한 JSON 배열로 받을 수 있습니다.
            return jsonHistoryList.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, Object.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception e) {
            log.error("Redis 히스토리 조회 실패 - guestUuid: {}", guestUuid, e);
            return List.of();
        }
    }


}
