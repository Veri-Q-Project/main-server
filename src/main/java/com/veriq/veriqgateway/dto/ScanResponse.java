package com.veriq.veriqgateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "QR 스캔 최종 결과 응답 객체")
public class ScanResponse {

    @Schema(description = "사용자 식별용 UUID", example = "sangmin-uuid-123")
    private String guestUuid;

    @Schema(description = "분석 진행 상태 (COMPLETED 또는 PROCESSING)", example = "COMPLETED")
    private String status;

    @JsonProperty("isUrl")
    @Schema(description = "URL 여부 확인", example = "true")
    private Boolean isUrl;

    // 🚨 핵심 포인트: allowableValues를 통해 Enum 종류를 명시합니다.
    @Schema(description = "URL 스키마 타입", example = "URL",
            allowableValues = {"URL", "TEL", "SMSTO", "MAILTO", "GEO", "MARKET", "OTHER"})
    private String schemeType;

    @Schema(description = "원본 URL 정보 또는 텍스트 데이터", example = "https://naver-login-check.xyz")
    private String typeInfo;

    // 🗑️ message와 error_code 필드는 삭제되었습니다.
}