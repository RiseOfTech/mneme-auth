package io.github.ifamneme.mneme_auth.domain;

import java.time.Instant;

public interface TokenSigner {

	String sign(UserId userId, Instant issuedAt);
}
