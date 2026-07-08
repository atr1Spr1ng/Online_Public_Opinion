package com.bupt.publicopinion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "jwt")
public record SystemProperties(
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration,
        String secret
) {
}
