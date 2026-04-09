package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.entity.ScanHistory;
import com.veriq.veriqbe3.repository.ScanHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProcessQrScan {

    private final QrDecoder qrDecoder;
    private final SchemeClassifier schemeClassifier;
    private final ScanHistoryRepository scanHistoryRepository;

    @Transactional // DB 저장 중 에러 발생 시 롤백하기 위함
    public QrScanResponse process(MultipartFile image, String guest_uuid) throws Exception {

        String url = qrDecoder.decode(image);         //이미지 디코드

        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(url);    //scheme 분류

        String status = "COMPLETED";

        if (result.type() == SchemeType.WEB || result.type() == SchemeType.SHORT_URL) {

            ScanHistory history = ScanHistory.builder()     //entity 생성
                    .guestUuid(guest_uuid)
                    .originalUrl(url)
                    .typeInfo(result.typeInfo())
                    .schemeType(result.type())
                    .analysisTime(response.analysisTime())
                    .totalScore(response.score())
                    .riskLevel(response.riskLevel())

                    // 1. HttpsInfo 빌드
                    .https(ScanHistory.HttpsInfo.builder()
                            .isSecure(response.https() != null && response.https().isSecure())
                            .build())

                    // 2. ShortUrlInfo 빌드
                    .shortUrl(ScanHistory.ShortUrlInfo.builder()
                            .isShortened(response.shortUrl() != null && response.shortUrl().isShortened())
                            .build())

                    // 3. MlInfo 빌드
                    .ml(ScanHistory.MlInfo.builder()
                            .threats(response.ml() != null && response.ml().threats() != null
                                    ? String.join(", ", response.ml().threats()) : null)
                            .mlScore(response.ml() != null ? response.ml().score() : 0)
                            .build())

                    // 4. ExternalApiInfo 빌드
                    .externalApi(ScanHistory.ExternalApiInfo.builder()
                            .apiChecked(response.externalApi() != null && response.externalApi().checked())
                            .apiProvider(response.externalApi() != null ? response.externalApi().provider() : null)
                            .apiResult(response.externalApi() != null ? response.externalApi().result() : null)
                            .build())

                    // 5. InternalDbInfo 빌드
                    .internalDb(ScanHistory.InternalDbInfo.builder()
                            .dbExists(response.internalDb() != null && response.internalDb().exists())
                            .dbReportCount(response.internalDb() != null ? response.internalDb().reportCount() : 0)
                            .dbBlockCount(response.internalDb() != null ? response.internalDb().blockCount() : 0)
                            .build())

                    // 6. RedirectInfo 빌드
                    .redirect(ScanHistory.RedirectInfo.builder()
                            .finalUrl(response.redirect() != null ? response.redirect().finalUrl() : url)
                            .redirectCount(response.redirect() != null ? response.redirect().redirectCount() : 0)
                            .build())

                    // 7. ServerInfo 및 CertificateInfo
                    .serverInfo(ScanHistory.ServerInfo.builder()
                            .serverType(response.serverInfo() != null ? response.serverInfo().type() : null)
                            .serverLocation(response.serverInfo() != null ? response.serverInfo().location() : null)
                            .certificate(response.serverInfo() != null && response.serverInfo().certificate() != null
                                    ? ScanHistory.CertificateInfo.builder()
                                    .certValid(response.serverInfo().certificate().valid())
                                    .certIssuer(response.serverInfo().certificate().issuer())
                                    .certValidFrom(response.serverInfo().certificate().validFrom())
                                    .certValidTo(response.serverInfo().certificate().validTo())
                                    .build()
                                    : null)
                            .build())
                    .build();

            scanHistoryRepository.save(scanHistory);            //repository에 저장

            return QrScanResponse.builder()
                    .guestUuid(guestUuid)
                    .schemeType(result.type())
                    .typeInfo(result.typeInfo())
                    .status("ANALYSIS_COMPLETED") // BE2 연동 후 실제 결과 상태로 변경
                    .build();
        }
        return QrScanResponse.builder()
                .guestUuid(guestUuid)
                .schemeType(result.type())
                .typeInfo(result.typeInfo())
                .status(status)
                .build();
    }
}