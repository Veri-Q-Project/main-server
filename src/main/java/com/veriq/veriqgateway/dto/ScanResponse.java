package com.veriq.veriqgateway.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor


public class ScanResponse {
    String guestUuid;             // 사용자 식별용
    private String status;      // PENDING 등
    private String error_code;  // REQUIRE_CAPTCHA
    private String message;
    @JsonProperty("isUrl")
    private boolean isUrl;
    private String schemeType;
    private String typeInfo;
}
