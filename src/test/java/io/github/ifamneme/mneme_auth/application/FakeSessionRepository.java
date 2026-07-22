package io.github.ifamneme.mneme_auth.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionId;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;

class FakeSessionRepository implements SessionRepository {

	private final Map<String, Session> generationsByTokenHash = new LinkedHashMap<>();
	private int revokeCalls = 0;

	@Override
	public Session save(Session generation) {
		generationsByTokenHash.put(generation.tokenHash().value(), generation);
		return generation;
	}

	@Override
	public Optional<Session> findByTokenHash(TokenHash tokenHash) {
		return Optional.ofNullable(generationsByTokenHash.get(tokenHash.value()));
	}

	@Override
	public boolean consumeIfLive(TokenHash tokenHash, Instant now) {
		Session existing = generationsByTokenHash.get(tokenHash.value());
		if (existing == null || existing.consumedAt() != null || !existing.expiresAt().isAfter(now)) {
			return false;
		}
		generationsByTokenHash.put(
				tokenHash.value(),
				new Session(
						existing.id(),
						existing.userId(),
						existing.tokenHash(),
						existing.expiresAt(),
						now,
						existing.createdAt(),
						now));
		return true;
	}

	@Override
	public void revokeLineage(SessionId sessionId, Instant now) {
		revokeCalls++;
		generationsByTokenHash.replaceAll((hash, session) -> {
			if (!session.id().equals(sessionId) || session.consumedAt() != null) {
				return session;
			}
			return new Session(
					session.id(), session.userId(), session.tokenHash(), session.expiresAt(), now, session.createdAt(), now);
		});
	}

	int size() {
		return generationsByTokenHash.size();
	}

	int revokeCalls() {
		return revokeCalls;
	}

	boolean isLive(TokenHash tokenHash, Instant now) {
		Session session = generationsByTokenHash.get(tokenHash.value());
		return session != null && session.consumedAt() == null && session.expiresAt().isAfter(now);
	}
}
