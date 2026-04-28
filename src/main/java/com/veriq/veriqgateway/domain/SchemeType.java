package com.veriq.veriqgateway.domain;

public enum SchemeType {
    WEB,
    SHORT_URL,
    OTP,
    CRYPTO,
    SMS,
    WIFI,
    CONTACT,
    DEEP_LINK,
    TEL,         // 전화번호 (tel:)
    EMAIL,       // 이메일 (mailto:)
    APP_STORE,   // 앱 설치 페이지 (market:, itms-apps:)
    OTHER        // 기타 단순 텍스트 등
}
