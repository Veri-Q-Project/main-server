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
        for (RiskLevel level : RiskLevel.values()) {
            if (level.value.equalsIgnoreCase(value)) {
                return level;
            }
        }
        return null; // 혹은 기본값 설정
    }
}