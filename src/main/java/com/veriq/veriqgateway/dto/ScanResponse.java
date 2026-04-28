package com.veriq.veriqgateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"guestUuid", "status", "isUrl", "schemeType", "typeInfo"})
@Schema(description = "QR 스캔 최종 결과 응답 객체")
public class ScanResponse {

    @Schema(description = "사용자 식별용 UUID", example = "sangmin-uuid-123")
    private String guestUuid;

    @Schema(description = "분석 진행 상태 (COMPLETED 또는 PROCESSING)", example = "COMPLETED")
    private String status;

    @JsonProperty("isUrl")
    @Schema(description = "URL 여부 확인", example = "true")
    private Boolean isUrl;

    @Schema(description = "URL 스키마 타입", example = "WEB",
            allowableValues = {"WEB", "SHORT_URL", "OTP", "CRYPTO", "SMS", "WIFI", "CONTACT", "DEEP_LINK", "TEL", "EMAIL", "APP_STORE", "OTHER"})
    private String schemeType;

    @Schema(description = "원본 URL 정보 또는 텍스트 데이터", example = "https://naver-login-check.xyz")
    private String typeInfo;

    // 🗑️ message와 error_code 필드는 삭제되었습니다.
}