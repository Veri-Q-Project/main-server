package com.veriq.veriqbe3.entity;

import com.veriq.veriqbe3.domain.SchemeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scan_history",indexes={@Index(name = "idx_final_url", columnList = "finalUrl")})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor

public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String guestUuid;
    @Column(length = 1000)
    private String originalUrl;

    private String typeInfo;

    @Enumerated(EnumType.STRING)
    private SchemeType schemeType;

    private LocalDateTime scannedAt;

    @PrePersist
    public void prePersist() {
        this.scannedAt = LocalDateTime.now();
    }

    private LocalDateTime analysisTime;
    private int totalScore;
    private String riskLevel;

    @Embedded private HttpsInfo https;
    @Embedded private ShortUrlInfo shortUrl;
    @Embedded private MlInfo ml;
    @Embedded private ExternalApiInfo externalApi;
    @Embedded private InternalDbInfo internalDb;
    @Embedded private RedirectInfo redirect;
    @Embedded private ServerInfo serverInfo;

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HttpsInfo { private boolean isSecure; }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ShortUrlInfo { private boolean isShortened; }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MlInfo {
        private String threats;
        private int mlScore;
    }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExternalApiInfo {
        private boolean apiChecked;
        private String apiProvider;
        private String apiResult;
    }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InternalDbInfo {
        private boolean dbExists;
        private Integer dbReportCount;
        private Integer dbBlockCount;
    }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RedirectInfo {
        private String finalUrl;
        private int redirectCount;
    }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ServerInfo {
        private String serverType;
        private String serverLocation;
        @Embedded private CertificateInfo certificate;
    }

    @Embeddable @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CertificateInfo {
        private boolean certValid;
        private String certIssuer;
        private LocalDateTime certValidFrom;
        private LocalDateTime certValidTo;
    }
}