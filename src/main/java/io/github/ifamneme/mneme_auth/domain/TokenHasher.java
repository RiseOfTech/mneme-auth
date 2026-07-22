package io.github.ifamneme.mneme_auth.domain;

public interface TokenHasher {

	TokenHash hash(RawRefreshToken rawRefreshToken);
}
