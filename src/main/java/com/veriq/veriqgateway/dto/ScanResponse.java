package com.veriq.veriqgateway.dto;
import lombok.Builder;
import lombok.Getter;
@Getter
@Builder


public class ScanResponse {
    String guestId;             // 사용자 식별용
    private String status;      // PENDING 등
    private String error_code;  // REQUIRE_CAPTCHA
    private String message;
}
