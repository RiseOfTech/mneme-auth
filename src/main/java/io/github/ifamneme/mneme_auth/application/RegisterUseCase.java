package io.github.ifamneme.mneme_auth.application;

public interface RegisterUseCase {

	AuthTokens register(String email, String rawPassword);
}
