package com.veriq.veriqbe3.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class RedisScanHistoryDto {
    private String schemeType;
    private String typeInfo;// 프론트에서 보여줄 원본 URL (예: https://www.naver.com)
    //private String status;
    private String scannedAt;    // 스캔시간
    private String riskLevel;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 편의 메서드: QrScanResponse 객체를 받아서 Redis용 객체로 쏙 변환해줍니다.
    public static RedisScanHistoryDto from(AnalysisResponse mlResponse) {
        Objects.requireNonNull(mlResponse, "mlResponse가 null일 수 없습니다!");
        return RedisScanHistoryDto.builder()
                .schemeType("URL") // 분석서버에서 callback해서 온것이므로 무조건 url
                .typeInfo(mlResponse.originalUrl())
                //.status(response.getStatus())
                .scannedAt(LocalDateTime.now().format(FORMATTER))
                .riskLevel(mlResponse.riskLevel() != null ? mlResponse.riskLevel().name() : "SUSPICIOUS")
                .build();
    }
}
