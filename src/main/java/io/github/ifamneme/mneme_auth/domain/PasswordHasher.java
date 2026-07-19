package io.github.ifamneme.mneme_auth.domain;

public interface PasswordHasher {

	PasswordHash hash(String rawPassword);

	boolean matches(String rawPassword, PasswordHash hash);
}
