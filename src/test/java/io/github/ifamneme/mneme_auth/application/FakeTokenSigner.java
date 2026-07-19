package io.github.ifamneme.mneme_auth.application;

import java.time.Instant;

import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.UserId;

class FakeTokenSigner implements TokenSigner {

	@Override
	public String sign(UserId userId, Instant issuedAt) {
		return "token:" + userId.value() + ":" + issuedAt;
	}
}
