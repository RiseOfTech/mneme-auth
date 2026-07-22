package io.github.ifamneme.mneme_auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;

class SecureRandomRefreshTokenGeneratorTest {

	private final SecureRandomRefreshTokenGenerator generator = new SecureRandomRefreshTokenGenerator();

	@Test
	void generatesAUrlSafeEncodedTwoHundredFiftySixBitToken() {
		RawRefreshToken token = generator.generate();

		byte[] decoded = Base64.getUrlDecoder().decode(token.value());
		assertThat(decoded).hasSize(32); // 256 bits
	}

	@Test
	void generatesDistinctTokensOnEachCall() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			tokens.add(generator.generate().value());
		}

		assertThat(tokens).hasSize(1000);
	}
}
