package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;

class FakePasswordHasher implements PasswordHasher {

	private static final String PREFIX = "{fake}";

	@Override
	public PasswordHash hash(String rawPassword) {
		return new PasswordHash(PREFIX + rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, PasswordHash hash) {
		return hash.value().equals(PREFIX + rawPassword);
	}
}
