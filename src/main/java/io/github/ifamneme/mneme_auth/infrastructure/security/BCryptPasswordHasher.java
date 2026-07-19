package io.github.ifamneme.mneme_auth.infrastructure.security;

import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

	private static final String ENCODING_ID = "bcrypt";
	private static final int STRENGTH = 12;

	private final PasswordEncoder encoder =
			new DelegatingPasswordEncoder(ENCODING_ID, Map.of(ENCODING_ID, new BCryptPasswordEncoder(STRENGTH)));

	@Override
	public PasswordHash hash(String rawPassword) {
		return new PasswordHash(encoder.encode(rawPassword));
	}

	@Override
	public boolean matches(String rawPassword, PasswordHash hash) {
		return encoder.matches(rawPassword, hash.value());
	}
}
