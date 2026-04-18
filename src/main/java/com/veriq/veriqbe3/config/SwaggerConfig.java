package com.veriq.veriqbe3.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI veriQOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Veri-Q API Server")
                        .description("스캔 리스트 ,상세보고서 제공,sse통로 구축 관련 api 명세서입니다.")
                        .version("v1.0.0"));
    }
}
