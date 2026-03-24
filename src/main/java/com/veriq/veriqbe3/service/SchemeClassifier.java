package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import org.springframework.stereotype.Component;

@Component
public class SchemeClassifier {

    // 💡 1. 두 개의 값을 한 번에 반환하기 위한 전용 포장지 (Record)
    public record ClassificationResult(SchemeType type, String typeInfo) {}

    // 💡 2. public으로 열어주고, 파라미터로 DTO를 받지 않습니다. 오직 텍스트만 받습니다.
    public ClassificationResult classify(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return new ClassificationResult(SchemeType.OTHER, rawText);
        }

        String lowerText = rawText.toLowerCase();

        // 1. 전화번호 (tel:01012345678)
        if (lowerText.startsWith("tel:")) {
            return new ClassificationResult(SchemeType.TEL, rawText.substring(4));
        }

        // 2. 이메일 (mailto:test@veriq.com)
        if (lowerText.startsWith("mailto:")) {
            return new ClassificationResult(SchemeType.EMAIL, rawText.substring(7));
        }

        // 3. 앱 스토어 (안드로이드 마켓 또는 iOS 앱스토어)
        if (lowerText.startsWith("market:") || lowerText.startsWith("itms-apps:")) {
            return new ClassificationResult(SchemeType.APP_STORE, rawText);
        }

        // 4. 웹 URL, 단축 URL 판별
        if (lowerText.startsWith("http://") || lowerText.startsWith("https://")) {
            return new ClassificationResult(SchemeType.WEB, rawText);
        }

        return new ClassificationResult(SchemeType.OTHER, rawText);
    }
}