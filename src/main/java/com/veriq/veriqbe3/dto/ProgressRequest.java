package com.veriq.veriqbe3.dto;

import jakarta.validation.constraints.NotBlank;
public record ProgressRequest(
        @NotBlank String guestUuid, // 식별자 (바디)
        String step,      // UI 매핑용 키값 (예: "내부 db조회 ", "AI 위험 분석")
        String status,    // 상태 (예: "IN_PROGRESS", "COMPLETED")
        String message    // 프론트에 띄울 실제 문구
) {
}