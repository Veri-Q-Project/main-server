package com.veriq.veriqbe3.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RiskLevel {
    SAFE("safe"),
    SUSPICIOUS("suspicious"),
    DANGER("danger");

    private final String value;

    RiskLevel(String value) {
        this.value = value;
    }

    @JsonValue // JSON으로 변환될 때나 응답할 때 소문자 값을 사용함
    public String getValue() {
        return value;
    }

    @JsonCreator // JSON의 문자열을 Enum으로 매핑할 때 사용함
    public static RiskLevel from(String value) {
        if (value == null || value.isBlank()) return RiskLevel.SUSPICIOUS; // null 방어

        for (RiskLevel level : RiskLevel.values()) {
            if (level.value.equalsIgnoreCase(value) || level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        // 매칭되는 게 없을 때 null 대신 의심(SUSPICIOUS) 상태로 기본값 설정
        return RiskLevel.SUSPICIOUS;
    }
}
