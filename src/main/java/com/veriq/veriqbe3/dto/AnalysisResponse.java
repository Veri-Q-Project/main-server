package com.veriq.veriqbe3.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Schema(
                    // 1. description에 카테고리별로 예쁘게 정리해서 넣어줍니다.
                    description = "###  탐지 위협 카테고리 상세\n" +
                            "**1. 리다이렉트 관련**\n" +
                            "- `too_many_redirects`, `loop_detected`, `invalid_location`\n\n" +
                            "**2. URL 구조 관련**\n" +
                            "- `shortened_url`, `embedded_url`, `percent_encoding_detected`, `double_encoding_suspected`\n\n" +
                            "**3. 구글 세이프 브라우징(GSB)**\n" +
                            "- `MALWARE`, `SOCIAL_ENGINEERING`, `UNWANTED_SOFTWARE`, `POTENTIALLY_HARMFUL_APPLICATION`\n\n" +
                            "**4. 기타 탐지**\n" +
                            "- `suspicious_param_detected`, `suspicious_keyword_detected`",

                    // 2. 실제 값 리스트는 가흔님 가이드대로 플래그들만 나열합니다.
                    allowableValues = {
                            "too_many_redirects", "loop_detected", "invalid_location",
                            "shortened_url", "embedded_url", "percent_encoding_detected", "double_encoding_suspected",
                            "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION",
                            "suspicious_param_detected", "suspicious_keyword_detected"
                    },
                    example = "['too_many_redirects', 'MALWARE']"
            )
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