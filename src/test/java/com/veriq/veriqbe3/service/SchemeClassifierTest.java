package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SchemeClassifierTest {

    private SchemeClassifier schemeClassifier;

    @BeforeEach
    void setUp() {
        // 의존성이 없으므로 객체를 직접 생성합니다. (가볍고 빠름)
        schemeClassifier = new SchemeClassifier();
    }

    @ParameterizedTest(name = "[{index}] input: {0} => expected: {1}")
    @CsvSource({
            // 기존 케이스들...
            "https://www.google.com, WEB",
            "https://bit.ly/3xyz, SHORT_URL",
            "otpauth://totp/Example:alice@google.com?secret=JBSWY3DPEHPK3PXP, OTP",
            "bitcoin:1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa?amount=0.1, CRYPTO",
            "tel:010-1234-5678, TEL",
            "mailto:test@example.com?subject=Hello, EMAIL",
            "market://details?id=com.example.app, APP_STORE",
            "myapp://open/page, DEEP_LINK",

            // 1. SMS 매칭 확인
            "sms:010-1234-5678, SMS",
            "sms:+821012345678?body=hello, SMS",

            // 2. WIFI 매칭 확인
            "WIFI:T:WPA;S:MyNetwork;P:password;;, WIFI",
            "WIFI:S:OpenNetwork;;, WIFI",

            // 3. CONTACT (vCard, MeCard) 매칭 확인
            "BEGIN:VCARD\\nVERSION:3.0\\nN:Doe;John;;;\\nEND:VCARD, CONTACT",
            "'MECARD:N:Doe,John;;', CONTACT",

            // 4. APP_STORE (itms-apps 분기) 매칭 확인
            "itms-apps://itunes.apple.com/app/id123456789, APP_STORE",

            // 5. 아무것에도 매칭되지 않는 일반 텍스트 (최하단 OTHER 반환 확인)
            "그냥 일반적인 텍스트 메시지입니다., OTHER",
            "123456, OTHER"
    })
    void classify_Success(String input, SchemeType expectedType) {
        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(input);
        assertThat(result.type()).isEqualTo(expectedType);
    }
}
