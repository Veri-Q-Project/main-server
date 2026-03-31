package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SchemeClassifier {

    // 1. 웹url(http/https 생략된 도메인 형태 포함)
    private static final Pattern WEB_URL_PATTERN = Pattern.compile(
            "^(https?://)?([\\da-z.-]+)\\.([a-z.]{2,})([/\\w .-?&#=]*)*/?$",   //http생략한 경우 포함, 한글자 도메인 이름 포함, query/fragment 허용, TLD 길이 제한 없음
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> SHORTENER_DOMAINS = List.of("bit.ly", "t.co", "goo.gl", "tinyurl.com");

    // 2. 2차 인증 (OTP) - otpauth://totp/Google:test@gmail.com?secret=...
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "^otpauth://(totp|hotp)/.*$",
            Pattern.CASE_INSENSITIVE
    );

    // 3. 가상화폐 지갑 - bitcoin:1A1zP1eP...
    private static final Pattern CRYPTO_PATTERN = Pattern.compile(
            "^(bitcoin|ethereum|litecoin|bitcoincash):[a-zA-Z0-9]+$",
            Pattern.CASE_INSENSITIVE
    );

    // 4. SMS - sms:+821012345678?body=hello
    private static final Pattern SMS_PATTERN = Pattern.compile(
            "^sms:(\\+?[0-9\\-]+)(\\?body=.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // 5. WI-FI - WIFI:T:WPA;S:MyNetwork;P:password;; (S and T can appear in any order, P is optional)
    private static final Pattern WIFI_PATTERN = Pattern.compile(
            "^WIFI:(?=.*S:[^;]+)(?=.*T:[^;]+)(?:S:[^;]+;|T:[^;]+;|P:[^;]+;){2,3};$",
            Pattern.CASE_INSENSITIVE
    );

    // 6. 연락처 (vCard / MeCard)
    private static final Pattern CONTACT_PATTERN = Pattern.compile(
            "^(BEGIN:VCARD|MECARD:).*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // 7. 전화번호 (tel:01012345678 또는 순수 숫자 9~13자리)
    private static final Pattern TEL_PATTERN = Pattern.compile(
            "^(tel:)?((\\+?82|0)1[016789]-?\\d{3,4}-?\\d{4})$",
            Pattern.CASE_INSENSITIVE
    );

    // 8. 이메일 (mailto:user@example.com)
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^mailto:[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    // 9. 앱 스토어 (market://..., itms-apps://...)
    private static final Pattern APP_STORE_PATTERN = Pattern.compile(
            "^(market|itms-apps)://.*$",
            Pattern.CASE_INSENSITIVE
    );

    // 10. 일반 딥 링크 (특정 앱 실행용 커스텀 스킴 instagram:)
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

        // 형식이 명확한 순서로 검사

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
            boolean isShortener = SHORTENER_DOMAINS.stream()
                    .anyMatch(domain -> trimmed.toLowerCase().contains(domain));

            if (isShortener) {
                return new ClassificationResult(SchemeType.SHORT_URL, trimmed, true);
            }
            return new ClassificationResult(SchemeType.WEB, trimmed, true);
        }

        if (DEEP_LINK_PATTERN.matcher(trimmed).matches()) {
            return new ClassificationResult(SchemeType.DEEP_LINK, trimmed, false);
        }

        // 그 외 일반 텍스트
        return new ClassificationResult(SchemeType.OTHER, trimmed, false);
    }
}