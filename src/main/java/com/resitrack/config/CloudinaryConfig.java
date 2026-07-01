package com.resitrack.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cloudinary client configuration.
 *
 * Reads credentials from environment variables only — never hardcoded.
 * On Render, set these as Environment Variables on the service:
 *   CLOUDINARY_CLOUD_NAME
 *   CLOUDINARY_API_KEY
 *   CLOUDINARY_API_SECRET
 */
@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Bean
    public Cloudinary cloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true // always return https secure_url
        ));
    }
}
