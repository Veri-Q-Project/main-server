package com.veriq.veriqgateway.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
@Getter
@NoArgsConstructor

public class GoogleCaptchaResponse {
    private boolean success; // 진짜 사람이면 true
    private String challenge_ts; // 인증 시간
    private String hostname; // 요청된 도메인

    @JsonProperty("error-codes")
    private List<String> errorCodes; // 실패 시 에러 코드들
}
