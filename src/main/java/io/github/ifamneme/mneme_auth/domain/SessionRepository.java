package io.github.ifamneme.mneme_auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface SessionRepository {

	Session save(Session generation);

	Optional<Session> findByTokenHash(TokenHash tokenHash);

	boolean consumeIfLive(TokenHash tokenHash, Instant now);

	void revokeLineage(SessionId sessionId, Instant now);
}
