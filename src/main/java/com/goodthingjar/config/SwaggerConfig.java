package com.goodthingjar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openApi(
        @Value("${spring.application.name}") String applicationName,
        @Value("${APP_VERSION:1.0.0}") String appVersion
    ) {
        return new OpenAPI()
            .info(new Info()
                .title("Good Thing Jar Backend API")
                .description("REST API documentation for the Good Thing Jar backend service")
                .version(appVersion)
            )
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
            .components(new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME_NAME,
                    new SecurityScheme()
                        .name("Authorization")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Provide a JWT access token in the Authorization header")
                )
            )
            .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                .name("System")
                .description("Auto-generated API for " + applicationName)
            );
    }
}

