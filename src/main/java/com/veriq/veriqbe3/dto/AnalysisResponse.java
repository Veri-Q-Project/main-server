package com.veriq.veriqbe3.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.veriq.veriqbe3.domain.RiskLevel;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResponse(
        String analysisTime,
        String originalUrl,
        HttpsInfo https,
        ShortUrlInfo shortUrl,
        MlInfo ml,
        ExternalApiInfo externalApi,
        Integer reportCount,
        Integer blockCount,
        String domainAge,
        RedirectInfo redirect,
        ServerInfo serverInfo,
        Integer score,
        RiskLevel riskLevel
) {
    // 🚨 [무식하지만 100% 확실한 방법] Jackson 멱살 잡고 매핑 강제 지시
    @JsonCreator
    public AnalysisResponse(
            @JsonProperty("analysisTime") @JsonAlias({"analysis_time"}) String analysisTime,
            @JsonProperty("originalUrl") @JsonAlias({"original_url", "url"}) String originalUrl,
            @JsonProperty("https") HttpsInfo https,
            @JsonProperty("shortUrl") ShortUrlInfo shortUrl,
            @JsonProperty("ml") MlInfo ml,
            @JsonProperty("externalApi") @JsonAlias({"safe_browsing"}) ExternalApiInfo externalApi,
            @JsonProperty("reportCount") @JsonAlias({"report_count"}) Integer reportCount,
            @JsonProperty("blockCount") @JsonAlias({"block_count"}) Integer blockCount,
            @JsonProperty("domainAge") @JsonAlias({"domain_age"}) String domainAge,
            @JsonProperty("redirect") RedirectInfo redirect,
            @JsonProperty("serverInfo") ServerInfo serverInfo,
            @JsonProperty("score") Integer score,
            @JsonProperty("riskLevel") RiskLevel riskLevel
    ) {
        this.analysisTime = analysisTime;
        this.originalUrl = originalUrl;
        this.https = https;
        this.shortUrl = shortUrl;
        this.ml = ml;
        this.externalApi = externalApi;
        this.reportCount = reportCount;
        this.blockCount = blockCount;
        this.domainAge = domainAge;
        this.redirect = redirect;
        this.serverInfo = serverInfo;
        this.score = score;
        this.riskLevel = riskLevel;
    }

    public record HttpsInfo(
            boolean isSecure
    ) {}

    public record ShortUrlInfo(
            boolean isShortened
    ) {}

    public record MlInfo(
            @Schema(
                    description = "###  탐지 위협 카테고리 상세\n" +
                            "**1. 리다이렉트 관련**\n" +
                            "- `too_many_redirects`, `loop_detected`, `invalid_location`\n\n" +
                            "**2. URL 구조 관련**\n" +
                            "- `shortened_url`, `embedded_url`, `percent_encoding_detected`, `double_encoding_suspected`\n\n" +
                            "**3. 구글 세이프 브라우징(GSB)**\n" +
                            "- `MALWARE`, `SOCIAL_ENGINEERING`, `UNWANTED_SOFTWARE`, `POTENTIALLY_HARMFUL_APPLICATION`\n\n" +
                            "**4. 기타 탐지**\n" +
                            "- `suspicious_param_detected`, `suspicious_keyword_detected`",
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
            String result
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
            String validFrom,
            String validTo
    ) {}
}