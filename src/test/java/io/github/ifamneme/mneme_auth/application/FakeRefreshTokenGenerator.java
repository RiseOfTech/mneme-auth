package io.github.ifamneme.mneme_auth.application;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;

class FakeRefreshTokenGenerator implements RefreshTokenGenerator {

	private final AtomicInteger counter = new AtomicInteger();

	@Override
	public RawRefreshToken generate() {
		return new RawRefreshToken("raw-token-" + counter.incrementAndGet());
	}
}
