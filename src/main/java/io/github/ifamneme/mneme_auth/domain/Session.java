package io.github.ifamneme.mneme_auth.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record Session(
		SessionId id,
		UserId userId,
		TokenHash tokenHash,
		Instant expiresAt,
		Instant consumedAt,
		Instant createdAt,
		Instant updatedAt) {

	public Session {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(tokenHash, "tokenHash must not be null");
		Objects.requireNonNull(expiresAt, "expiresAt must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	public static Session open(UserId userId, TokenHash tokenHash, Instant now, Duration idleWindow) {
		return new Session(SessionId.generate(), userId, tokenHash, now.plus(idleWindow), null, now, now);
	}

	public Session rotate(TokenHash newTokenHash, Instant now, Duration idleWindow) {
		return new Session(id, userId, newTokenHash, now.plus(idleWindow), null, now, now);
	}
}
