package com.veriq.veriqgateway.dto;
import lombok.Builder;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonProperty;
@Getter
@Builder


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
