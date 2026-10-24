// 주인: BE-A
package com.ieum.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS: FE 주소(app.frontend-origin)만 허용 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.frontend-origin}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
