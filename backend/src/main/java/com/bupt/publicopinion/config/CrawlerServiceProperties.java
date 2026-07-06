package com.bupt.publicopinion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "crawler.service")
public record CrawlerServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
