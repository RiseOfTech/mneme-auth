package io.github.ifamneme.mneme_auth.application;

public interface LoginUseCase {

	String login(String email, String rawPassword);
}
