package io.github.ifamneme.mneme_auth.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mneme.auth.refresh")
public record RefreshTokenProperties(Duration idleWindow, String cookieName, String cookieDomain) {}
