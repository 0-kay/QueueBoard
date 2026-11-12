package com.liberty.queueboardbe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI queueBoardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("QueueBoard API")
                .version("v1")
                .description("""
                        Submit background jobs, watch them move through QUEUED → PROCESSING → COMPLETED or FAILED, \
                        inspect results and errors, and retry failed jobs when the rules allow. \
                        Every response is wrapped in ApiResult."""));
    }
}
