package com.veriq.veriqbe3.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime analysisTime,
        String originalUrl,
        HttpsInfo https,
        ShortUrlInfo shortUrl,
        MlInfo ml,
        ExternalApiInfo externalApi,
        InternalDbInfo internalDb,
        RedirectInfo redirect,
        ServerInfo serverInfo,
        int score,
        String riskLevel
) {
    public record HttpsInfo(
            boolean isSecure
    ) {}

    public record ShortUrlInfo(
            boolean isShortened
    ) {}

    public record MlInfo(
            List<String> threats,
            int score
    ) {}

    public record ExternalApiInfo(
            boolean checked,
            String provider,
            String result // 실패/결과 없을 시 null 대응을 위해 String 유지
    ) {}

    public record InternalDbInfo(
            boolean exists,
            Integer reportCount, // null 대응을 위해 래퍼 클래스 Integer 사용
            Integer blockCount
    ) {}

    public record RedirectInfo(
            String finalUrl,
            int redirectCount
    ) {}

    public record ServerInfo(
            String type,
            String location,
            CertificateInfo certificate
    ) {}

    public record CertificateInfo(
            boolean valid,
            String issuer,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
            LocalDateTime validFrom,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
            LocalDateTime validTo
    ) {}
}