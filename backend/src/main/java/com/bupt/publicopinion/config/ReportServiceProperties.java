package com.bupt.publicopinion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "report.service")
public record ReportServiceProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
