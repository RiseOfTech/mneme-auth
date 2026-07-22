package io.github.ifamneme.mneme_auth.application;

public interface RefreshUseCase {

	AuthTokens refresh(String rawRefreshToken);
}
