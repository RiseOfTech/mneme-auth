package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;

public record AuthTokens(String accessToken, RawRefreshToken refreshToken) {}
