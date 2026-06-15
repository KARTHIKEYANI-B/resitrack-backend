package com.resitrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // ── Hardcoded allowed origins ──────────────────────────────────────
        // Add every known Vercel deployment URL here.
        // The CORS_ALLOWED_ORIGINS env var on Render is an additional override
        // for any future URLs without requiring a redeploy.
        List<String> origins = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "https://resitrack-seven.vercel.app",
            "https://resitrack-git-main-karthikeyani-bs-projects.vercel.app"
        ));

        // ── Additional origins from environment variable ───────────────────
        // Set CORS_ALLOWED_ORIGINS on Render to add more origins without code changes.
        // Format: comma-separated URLs, e.g.:
        //   https://my-app.vercel.app,https://custom-domain.com
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            for (String origin : allowedOriginsConfig.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !origins.contains(trimmed)) {
                    origins.add(trimmed);
                }
            }
        }

        config.setAllowedOrigins(origins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setMaxAge(3600L); // cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}