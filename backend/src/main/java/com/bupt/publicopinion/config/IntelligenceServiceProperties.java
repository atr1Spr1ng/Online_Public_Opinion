package com.bupt.publicopinion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "intelligence.service")
public record IntelligenceServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
