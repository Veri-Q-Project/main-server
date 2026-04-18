package com.veriq.veriqgateway.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI gatewayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Veri-Q Gateway (BE1) API")
                        .description("qr 이미지 서버에 전송하는 api 명세서입니다")
                        .version("v1.0.0"));
    }
}
