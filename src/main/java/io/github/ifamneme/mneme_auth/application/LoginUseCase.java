package io.github.ifamneme.mneme_auth.application;

public interface LoginUseCase {

	AuthTokens login(String email, String rawPassword);
}
