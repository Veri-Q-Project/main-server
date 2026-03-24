package com.veriq.veriqgateway.dto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CaptchaRequest {

    private String captchaToken; // 구글에서 준 토큰
    private String guestId;   // 초기화할 유저 ID
}
