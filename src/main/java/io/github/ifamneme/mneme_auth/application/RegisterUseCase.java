package io.github.ifamneme.mneme_auth.application;

public interface RegisterUseCase {

	String register(String email, String rawPassword);
}
