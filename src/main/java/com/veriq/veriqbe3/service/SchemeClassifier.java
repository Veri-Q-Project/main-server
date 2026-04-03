package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SchemeClassifier {

    // 1. 웹 URL DoS 방지 및 특수문자 % 허용
    private static final Pattern WEB_URL_PATTERN = Pattern.compile(
            "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,})([/a-zA-Z0-9.~:?#@!$&'()*+,;=%-]*)$",
            Pattern.CASE_INSENSITIVE
    );

    // 단축 URL 확인을 위한 패턴 (정확히 호스트명에 해당하는지 검사하기 위함)
    private static final List<Pattern> SHORTENER_PATTERNS = List.of(
            Pattern.compile("^(https?://)?(www\\.)?bit\\.ly(/.*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(https?://)?(www\\.)?t\\.co(/.*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(https?://)?(www\\.)?goo\\.gl(/.*)?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(https?://)?(www\\.)?tinyurl\\.com(/.*)?$", Pattern.CASE_INSENSITIVE)
    );

    // 2. 2차 인증 (OTP)
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "^otpauth://(totp|hotp)/.*$",
            Pattern.CASE_INSENSITIVE
    );

    // 3. 가상화폐 지갑 (?amount= 등 쿼리파라미터 허용)
    private static final Pattern CRYPTO_PATTERN = Pattern.compile(
            "^(bitcoin|ethereum|litecoin|bitcoincash):[a-zA-Z0-9]+(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // 4. SMS
    private static final Pattern SMS_PATTERN = Pattern.compile(
            "^sms:(\\+?[0-9\\-]+)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // 5. WI-FI (비밀번호가 없는 Open 네트워크도 존재할 수 있으므로 P:는 필수가 아님)
    private static final Pattern WIFI_PATTERN = Pattern.compile(
            "^WIFI:.*(S:[^;]+).*;;?$",
            Pattern.CASE_INSENSITIVE
    );

    // 6. 연락처 (vCard / MeCard)
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "^(BEGIN:VCARD|MECARD:).*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // 7. 전화번호 (일반 유선, 국제번호 포함하여 유연하게 변경)
    private static final Pattern TEL_PATTERN = Pattern.compile(
            "^(tel:)?(\\+?[0-9\\-]{8,15})$",
            Pattern.CASE_INSENSITIVE
    );

    // 8. 이메일 (?subject= 등 쿼리파라미터 허용)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^mailto:[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(\\?.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // 9. 앱 스토어
    private static final Pattern APP_STORE_PATTERN = Pattern.compile(
            "^(market|itms-apps)://.*$",
            Pattern.CASE_INSENSITIVE
    );

    // 10. 일반 딥 링크
    private static final Pattern DEEP_LINK_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9+-.]*://.*$",
            Pattern.CASE_INSENSITIVE
    );

    public record ClassificationResult(SchemeType type, String typeInfo, boolean toBe2) {}

    public ClassificationResult classify(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return new ClassificationResult(SchemeType.OTHER, rawText, false);
        }

        String trimmed = rawText.strip();

        if (OTP_PATTERN.matcher(trimmed).matches()) return new ClassificationResult(SchemeType.OTP, trimmed, false);
        if (CRYPTO_PATTERN.matcher(trimmed).matches()) return new ClassificationResult(SchemeType.CRYPTO, trimmed, false);
        if (WIFI_PATTERN.matcher(trimmed).matches()) return new ClassificationResult(SchemeType.WIFI, trimmed, false);
        if (SMS_PATTERN.matcher(trimmed).matches()) return new ClassificationResult(SchemeType.SMS, trimmed, false);
        if (CONTACT_PATTERN.matcher(trimmed).matches()) return new ClassificationResult(SchemeType.CONTACT, trimmed, false);

        Matcher telMatcher = TEL_PATTERN.matcher(trimmed);
        if (telMatcher.matches()) {
            String phoneNum = trimmed.replaceFirst("(?i)tel:", "");
            return new ClassificationResult(SchemeType.TEL, phoneNum, false);
        }

        Matcher emailMatcher = EMAIL_PATTERN.matcher(trimmed);
        if (emailMatcher.matches()) {
            String email = trimmed.replaceFirst("(?i)mailto:", "");
            return new ClassificationResult(SchemeType.EMAIL, email, false);
        }

        Matcher appStoreMatcher = APP_STORE_PATTERN.matcher(trimmed);
        if (appStoreMatcher.matches()) {
            String storeUri = trimmed.replaceFirst("(?i)(market|itms-apps)://", "");
            return new ClassificationResult(SchemeType.APP_STORE, storeUri, false);
        }

        if (WEB_URL_PATTERN.matcher(trimmed).matches()) {
            // 정규식을 통해 정확히 도메인 형태가 단축 URL인지 판별
            boolean isShortener = SHORTENER_PATTERNS.stream()
                    .anyMatch(pattern -> pattern.matcher(trimmed).matches());

            if (isShortener) {
                return new ClassificationResult(SchemeType.SHORT_URL, trimmed, true);
            }
            return new ClassificationResult(SchemeType.WEB, trimmed, true);
        }

        if (DEEP_LINK_PATTERN.matcher(trimmed).matches()) {
            return new ClassificationResult(SchemeType.DEEP_LINK, trimmed, false);
        }

        return new ClassificationResult(SchemeType.OTHER, trimmed, false);
    }
}