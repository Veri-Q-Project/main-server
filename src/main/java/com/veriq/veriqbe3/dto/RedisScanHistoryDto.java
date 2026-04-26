package com.veriq.veriqbe3.dto;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "QR 스캔 이력 상세 데이터")
public class RedisScanHistoryDto {
    @Schema(description = "URL 스키마 타입", example = "URL")
    private String schemeType;
    @Schema(description = "원본 URL 정보 또는 텍스트 내용", example = "http://naver-login-check.xyz")
    private String typeInfo;// 프론트에서 보여줄 원본 URL (예: https://www.naver.com)
    //private String status;
    @Schema(description = "스캔 일시", example = "2026-04-26T18:15:30")
    private String scannedAt;    // 스캔시간
    @Schema(description = "위험도 레벨 (SAFE, SUSPICIOUS, DANGER)", example = "DANGER")
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
                .riskLevel(mlResponse.riskLevel())
                .build();
    }
}
