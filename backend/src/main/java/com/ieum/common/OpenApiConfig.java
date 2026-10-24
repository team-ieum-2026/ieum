// 주인: BE-A
package com.ieum.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger 문서: http://localhost:8080/swagger-ui.html */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ieumOpenApi() {
        return new OpenAPI().info(new Info()
                .title("이음 API")
                .description("수료생 사후관리 AI 에이전트 이음 BE API. 명세는 docs/API.md")
                .version("P0"));
    }
}
