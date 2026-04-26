package com.veriq.veriqgateway.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor

@Schema(description = "QR 스캔 최종 결과 응답 객체")
public class ScanResponse {
    @Schema(description = "사용자 식별용 UUID", example = "sangmin-test-008")
    String guestUuid;// 사용자 식별용

    private String status;
    @Schema(description = "에러 코드 (캡차 필요 시: REQUIRE_CAPTCHA)", example = "null")
    private String error_code;  // REQUIRE_CAPTCHA
    @Schema(description = "사용자용 안내 메시지", example = "위험한 URL이 탐지되었습니다. 접속에 주의하세요.")
    private String message;
    @JsonProperty("isUrl")
    @Schema(description = "URL 여부 확인", example = "true")
    private Boolean isUrl;
    @Schema(description = "URL 스키마 타입 (URL, TEXT, MARKET 등)", example = "URL")
    private String schemeType;
    @Schema(description = "원본 URL 정보 또는 텍스트 내용", example = "http://naver-login-check.xyz")
    private String typeInfo;
}
