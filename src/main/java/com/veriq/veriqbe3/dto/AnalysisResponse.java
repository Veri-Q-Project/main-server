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

        // 🚨 [수정됨] GSB:, OTX: 접두사 제거 및 중복(MALWARE) 통합!
        @Schema(
                description = "### 🚨 탐지 위협 카테고리 상세\n" +
                        "**1. URL 구조 및 패턴**\n" +
                        "- `SHORTENED_URL`, `percent_encoding_detected`, `double_encoding_suspected`, `suspicious_query_param_detected`, `embedded_url`, `suspicious_query_keyword_detected`, `suspicious_path_keyword_detected`, `suspicious_fragment_keyword_detected`\n\n" +
                        "**2. 외부 위협 인텔리전스 (GSB, OTX 통합)**\n" +
                        "- `MALWARE`, `SOCIAL_ENGINEERING`, `UNWANTED_SOFTWARE`, `POTENTIALLY_HARMFUL_APPLICATION`, `PHISHING`, `RANSOMWARE`, `BOTNET`, `SPAM`, `C2`, `SUSPICIOUS`\n\n" +
                        "**3. 인증서 및 연결 오류**\n" +
                        "- `CERT_SELF_SIGNED`, `CERT_UNTRUSTED`, `CERT_EXPIRED`, `CERT_HOSTNAME_MISMATCH`, `CERT_NOT_YET_VALID`, `CERT_REVOKED`, `CERT_SSL_ERROR`, `CERT_INVALID_HOST`, `CERT_CONNECTION_FAILED`, `CERT_LOOKUP_FAILED`, `CERT_TIMEOUT`, `CERT_NO_CERTIFICATE`, `CERT_UNKNOWN_ERROR`",
                allowableValues = {
                        "SHORTENED_URL", "percent_encoding_detected", "double_encoding_suspected", "suspicious_query_param_detected",
                        "embedded_url", "suspicious_query_keyword_detected", "suspicious_path_keyword_detected", "suspicious_fragment_keyword_detected",
                        "MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION",
                        "PHISHING", "RANSOMWARE", "BOTNET", "SPAM", "C2", "SUSPICIOUS",
                        // 👇 기존 소문자 및 원문 에러를 지우고 새로운 규격 상수 추가!
                        "CERT_SELF_SIGNED", "CERT_UNTRUSTED", "CERT_EXPIRED", "CERT_HOSTNAME_MISMATCH",
                        "CERT_NOT_YET_VALID", "CERT_REVOKED", "CERT_SSL_ERROR", "CERT_INVALID_HOST",
                        "CERT_CONNECTION_FAILED", "CERT_LOOKUP_FAILED", "CERT_TIMEOUT", "CERT_NO_CERTIFICATE", "CERT_UNKNOWN_ERROR"
                },
                example = "['PHISHING', 'CERT_SELF_SIGNED']"
        )
        List<String> threats,

        @Schema(hidden = true)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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
    @JsonCreator
    public AnalysisResponse(
            @JsonProperty("analysisTime") @JsonAlias({"analysis_time"}) String analysisTime,
            @JsonProperty("originalUrl") @JsonAlias({"original_url", "url"}) String originalUrl,
            @JsonProperty("https") HttpsInfo https,
            @JsonProperty("shortUrl") ShortUrlInfo shortUrl,
            @JsonProperty("threats") List<String> threats,
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
        this.threats = threats;
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

    public record HttpsInfo(boolean isSecure) {}
    public record ShortUrlInfo(boolean isShortened) {}

    public record MlInfo(
            @Schema(hidden = true)
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            List<String> threats,

            @JsonAlias({"mlScore", "score"})
            Integer score
    ) {}

    public record ExternalApiInfo(
            boolean checked,
            String provider,

            @Schema(
                    description = "외부 API 통합 검사 결과",
                    allowableValues = {
                            "THREAT", "SAFE", "UNKNOWN",
                            "GSB_FAILED", "OTX_FAILED", "WHOIS_FAILED", "GSB_OTX_WHOIS_FAILED"
                    }
            )
            String result
    ) {}

    public record RedirectInfo(
            String finalUrl,

            @Schema(hidden = true)
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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