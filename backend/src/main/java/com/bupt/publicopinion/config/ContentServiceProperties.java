package com.bupt.publicopinion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "content.service")
public record ContentServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
