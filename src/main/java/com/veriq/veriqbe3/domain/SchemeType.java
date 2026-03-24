package com.veriq.veriqbe3.domain;

public enum SchemeType {
    WEB,         // 일반 웹 (http, https)
    SHORT_URL,   // 단축 URL
    TEL,         // 전화번호 (tel:)
    EMAIL,       // 이메일 (mailto:)
    APP_STORE,   // 앱 설치 페이지 (market:, itms-apps:)
    OTHER        // 기타 단순 텍스트 등
}


