package com.goodthingjar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}") String allowedOrigins,
        @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String allowedMethods,
        @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Requested-With,X-Request-Id}") String allowedHeaders,
        @Value("${app.cors.exposed-headers:X-Request-Id}") String exposedHeaders,
        @Value("${app.cors.max-age-seconds:3600}") long maxAgeSeconds
    ) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(toList(allowedOrigins));
        cors.setAllowedMethods(toList(allowedMethods));
        cors.setAllowedHeaders(toList(allowedHeaders));
        cors.setExposedHeaders(toList(exposedHeaders));
        cors.setAllowCredentials(true);
        cors.setMaxAge(maxAgeSeconds);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private List<String> toList(String csv) {
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }
}

