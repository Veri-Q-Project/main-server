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

    @Schema(
            // 1. 실제 Enum 클래스를 연결해서 스웨거가 목록을 인식하게 합니다.
            implementation = com.veriq.veriqgateway.domain.SchemeType.class,
            description = """
            URL 스키마 타입 상세 가이드:
            - **WEB**: 일반적인 웹사이트 주소 (http, https)
            - **SHORT_URL**: bit.ly 등 단축 URL 서비스
            - **OTP**: 2단계 인증용 일회용 비밀번호 생성
            - **CRYPTO**: 가상자산 지갑 주소 및 송금 요청
            - **SMS**: 문자 메시지 발송 (smsto:)
            - **WIFI**: 와이파이 네트워크 자동 접속 정보
            - **CONTACT**: 연락처(vCard) 자동 저장 정보
            - **DEEP_LINK**: 특정 앱의 특정 화면 실행 링크,instagram://kakaotalk://kakaopay/money/remit (카카오페이 송금 화면)
            - **TEL**: 전화 걸기 (tel:)
            - **EMAIL**: 이메일 작성 (mailto:)
            - **APP_STORE**: 앱 마켓(PlayStore, AppStore) 이동, 앱 설치 및 업데이트 페이지
            - **OTHER**: 기타 텍스트 및 미분류 타입
            """,
            example = "WEB",
            // 2. 명시적으로 순서나 허용 값을 한 번 더 잡아줍니다.
            allowableValues = {
                    "WEB", "SHORT_URL", "OTP", "CRYPTO", "SMS", "WIFI",
                    "CONTACT", "DEEP_LINK", "TEL", "EMAIL", "APP_STORE", "OTHER"
            }
    )
    private String schemeType;

    @Schema(description = "원본 URL 정보 또는 텍스트 데이터", example = "https://naver-login-check.xyz")
    private String typeInfo;

    // 🗑️ message와 error_code 필드는 삭제되었습니다.
}