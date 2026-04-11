package com.veriq.veriqbe3.dto;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
@Getter
@Builder

public class RedisScanHistoryDto {
    private String schemeType;
    private String typeInfo;// 프론트에서 보여줄 원본 URL (예: https://www.naver.com)
    //private String status;
    private String scannedAt;    // 스캔시간
    private String riskLevel;

    // 편의 메서드: QrScanResponse 객체를 받아서 Redis용 객체로 쏙 변환해줍니다.
    public static RedisScanHistoryDto from(QrScanResponse response) {
        return RedisScanHistoryDto.builder()
                .schemeType(response.getSchemeType().name())
                .typeInfo(response.getTypeInfo())
                //.status(response.getStatus())
                .scannedAt(LocalDateTime.now().toString())
                .riskLevel(response.getStatus())
                .build();
    }
}
