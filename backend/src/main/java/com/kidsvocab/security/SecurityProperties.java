package com.kidsvocab.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        boolean refreshCookieSecure,
        int pinMaxAttempts,
        Duration pinLockDuration) {
}
