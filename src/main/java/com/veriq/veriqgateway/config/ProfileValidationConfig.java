package com.veriq.veriqgateway.config;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor

public class ProfileValidationConfig {
    private final Environment env;

    @PostConstruct
    public void validateProfile() {
        // 현재 켜진 프로필 목록을 가져옵니다.
        String[] activeProfiles = env.getActiveProfiles();

        // 프로필이 하나도 설정되지 않았다면 강제로 예외를 발생시켜 서버를 끕니다!
        if (activeProfiles.length == 0) {
            throw new IllegalStateException(
                    "🚨 [치명적 에러] 활성화된 스프링 프로필(SPRING_PROFILES_ACTIVE)이 없습니다! " +
                            "배포 환경 변수나 로컬 실행 옵션에 'be1' 또는 'be3'를 반드시 설정하고 다시 실행해 주세요."
            );
        }
    }

}
