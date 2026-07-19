package io.github.ifamneme.mneme_auth.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties(prefix = "mneme.auth.token")
public record JwtProperties(String issuer, String audience, Duration ttl, String kid, Resource privateKeyLocation) {}
