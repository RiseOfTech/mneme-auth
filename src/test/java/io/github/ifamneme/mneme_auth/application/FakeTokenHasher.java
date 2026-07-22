package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;

class FakeTokenHasher implements TokenHasher {

	@Override
	public TokenHash hash(RawRefreshToken rawRefreshToken) {
		return new TokenHash("hash:" + rawRefreshToken.value());
	}
}
