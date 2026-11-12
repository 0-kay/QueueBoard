package com.liberty.queueboardbe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the separately built frontend call the API from its dev server. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final QueueBoardProperties properties;

    public WebConfig(QueueBoardProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .exposedHeaders("Location");
    }
}
