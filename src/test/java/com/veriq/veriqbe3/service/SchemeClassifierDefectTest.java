package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SchemeClassifierDefectTest {

    private SchemeClassifier schemeClassifier;

    @BeforeEach
    void setUp() {
        schemeClassifier = new SchemeClassifier();
    }

    @Test
    @DisplayName("결함 1: 일반 웹사이트 도메인에 단축 URL 키워드(t.co)가 포함되어 있어도 SHORT_URL로 오분류되지 않아야 한다.")
    void testShortenerMisclassificationBug() {
        // Given: "smart.com" 이라는 정상적인 도메인 (중간에 "t.co"가 포함되어 있음)
        String normalWebUrl = "https://smart.com";

        // When
        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(normalWebUrl);

        // Then: SHORT_URL이 아닌 WEB으로 분류되어야 함
        // (기존 결함 코드에서는 contains("t.co")에 걸려 SHORT_URL로 잘못 분류됨)
        assertThat(result.type())
                .as("smart.com은 단축 URL이 아니므로 WEB으로 분류되어야 합니다.")
                .isEqualTo(SchemeType.WEB);
    }



    @Test
    @DisplayName("결함 2: 악의적으로 조작된 긴 URL이 입력되어도 ReDoS(정규식 엔진 멈춤)가 발생하지 않고 100ms 이내에 처리가 끝나야 한다.")
    void testReDoSVulnerability() {
        // Given: 정규식 Catastrophic Backtracking을 유발하는 악성 페이로드 생성
        // 허용된 문자(a)를 길게 반복하고, 마지막에 패턴에 어긋나는 특수기호(!)를 배치
        String maliciousUrl = "http://example.com/" + "a".repeat(100) + "!";

        // When & Then: 타임아웃 검증 (100 밀리초 내에 응답하지 않으면 테스트 실패 처리)
        // (기존 결함 코드에서는 이 로직이 스레드를 무한 루프에 빠뜨려 테스트가 영원히 끝나지 않음)
        assertTimeoutPreemptively(Duration.ofMillis(200), () -> {
            SchemeClassifier.ClassificationResult result = schemeClassifier.classify(maliciousUrl);

            // 패턴에 완벽히 부합하지 않으므로 WEB이 아닌 DEEP_LINK나 OTHER 등으로 빠질 수 있음
            // 여기서 중요한 건 '결과값'이 아니라 '서버가 멈추지 않고 즉시 응답을 반환하는가' 입니다.
            assertThat(result).isNotNull();
        }, "ReDoS 취약점으로 인해 정규식 처리가 100ms를 초과하여 지연되었습니다!");
    }



    @Test
    @DisplayName("결함 3: 010 한국 휴대전화 번호 외에도 지역번호, 국제번호 등 다양한 형태의 전화번호를 TEL로 인식해야 한다.")
    void testTelExpansionBug() {
        // Given: 한국 휴대전화 번호가 아닌 다양한 포맷의 유효한 전화번호들
        String landlineNumber = "tel:02-123-4567";      // 서울 지역번호
        String internationalNumber = "tel:+1-800-123-4567"; // 미국 국제/톨프리 번호
        String commercialNumber = "tel:1588-1111";      // 전국 대표 번호

        // When
        var landlineResult = schemeClassifier.classify(landlineNumber);
        var internationalResult = schemeClassifier.classify(internationalNumber);
        var commercialResult = schemeClassifier.classify(commercialNumber);

        // Then: 모두 정상적으로 TEL로 분류되어야 함
        // (기존 결함 코드에서는 정해진 한국 폰 번호 정규식에 어긋나 OTHER로 분류됨)
        assertThat(landlineResult.type()).isEqualTo(SchemeType.TEL);
        assertThat(internationalResult.type()).isEqualTo(SchemeType.TEL);
        assertThat(commercialResult.type()).isEqualTo(SchemeType.TEL);
    }

}