package com.veriq.veriqbe3.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.veriq.veriqbe3.domain.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]")
        LocalDateTime analysisTime,
        String originalUrl,
        HttpsInfo https,
        ShortUrlInfo shortUrl,
        MlInfo ml,
        ExternalApiInfo externalApi,
        InternalDbInfo internalDb,
        RedirectInfo redirect,
        ServerInfo serverInfo,
        Integer score,
        RiskLevel riskLevel
) {
    public record HttpsInfo(
            boolean isSecure
    ) {}

    public record ShortUrlInfo(
            boolean isShortened
    ) {}

    public record MlInfo(
            List<String> threats,
            @JsonAlias({"mlScore", "score"})
            Integer score
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
            Integer redirectCount
    ) {}

    public record ServerInfo(
            String type,
            String location,
            CertificateInfo certificate
    ) {}

    public record CertificateInfo(
            boolean valid,
            String issuer,
            // 🚨 [여기가 핵심 수정 포인트!]
            // 파이썬의 "Mar 10 00:00:00 2026 GMT" 형식을 읽기 위한 포맷입니다.
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM dd HH:mm:ss yyyy z", locale = "en",timezone = "GMT")
            LocalDateTime validFrom,

            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MMM dd HH:mm:ss yyyy z", locale = "en",timezone = "GMT")
            LocalDateTime validTo
    ) {}
}