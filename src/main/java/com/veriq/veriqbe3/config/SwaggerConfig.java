package com.veriq.veriqbe3.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Veri-Q API 분석 명세서",
                version = "v1.0",
                description = """
        ## 🚀 Veri-Q QR 분석 전체 시퀀스 (프론트엔드 필독)
        
        분석의 신뢰성과 실시간성 보장을 위해 아래 **10단계 순서**를 반드시 준수해야 합니다.
        
        1. **SSE 연결 (Subscribe)**: 프론트엔드가 BE3의 `/subscribe`에 연결합니다. **(반드시 분석 요청보다 먼저 수행!)**
        2. **INIT 수신**: BE3로부터 `INIT` 채널을 통해 연결 성공 신호를 받습니다.
        3. **분석 요청**: 프론트엔드가 BE1(Gateway)으로 QR 이미지를 전송합니다.
        4. **내부 전달**: BE1이 검증 후 BE3로 이미지를 전달합니다.
        5. **분석 트리거**: BE3가 FastAPI로 분석을 요청합니다.
        6. **중간보고 수신**: FastAPI가 BE3로 분석 단계(Progress)를 전달합니다.
        7. **진행 상황 중계**: BE3가 프론트엔드로 `progress` 채널을 통해 현재 단계를 실시간 전송합니다.
        8. **분석 완료**: FastAPI가 최종 결과를 BE3로 전달합니다.
        9. **완료 알림**: BE3가 DB/Redis 저장 후 `COMPLETE` 채널로 분석 완료 및 **원본 URL**을 전송합니다.
        10. **상세 결과 조회**: 프론트엔드가 `COMPLETE`에서 받은 URL을 사용하여 `/detail` API를 호출합니다.
        """
        )
)
public class SwaggerConfig {
    // @OpenAPIDefinition을 통해 API 메타데이터가 정의되었으므로
    // 클래스 내부에 추가적인 Bean 설정 없이도 스웨거 페이지에 반영됩니다.
}
