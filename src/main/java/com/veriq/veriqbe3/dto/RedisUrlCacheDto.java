package com.veriq.veriqbe3.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class RedisUrlCacheDto {
    private String resultStatus;    // SAFE, WARNING, CRITICAL
    private int trustScore;         // 85점
    private String riskLevel;       // 낮음
    private String siteName;        // 네이버
    private String sslStatus;       // 유효함
    private String domainCreated;   // 1997.12.15
    // 그 외  결과 페이지에 들어가는 모든 텍스트 정보들...
}
