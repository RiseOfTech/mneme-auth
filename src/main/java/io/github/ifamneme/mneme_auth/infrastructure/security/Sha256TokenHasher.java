package io.github.ifamneme.mneme_auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;

@Component
public class Sha256TokenHasher implements TokenHasher {

	@Override
	public TokenHash hash(RawRefreshToken rawRefreshToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawRefreshToken.value().getBytes(StandardCharsets.UTF_8));
			return new TokenHash(HexFormat.of().formatHex(hashed));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm unavailable", e);
		}
	}
}
