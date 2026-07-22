package io.github.ifamneme.mneme_auth.infrastructure.security;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;

@Component
public class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

	private static final int TOKEN_BYTES = 32; // 256 bits

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public RawRefreshToken generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return new RawRefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}
}
