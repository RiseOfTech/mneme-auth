package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionId;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.UserId;

@Component
class DefaultRefreshTokenMapper implements RefreshTokenMapper {

	@Override
	public RefreshTokenRecord toRecord(Session generation) {
		// The record's own id is the row's technical PK, distinct from SessionId (the
		// lineage id shared by every generation row); it has no domain counterpart, so the
		// mapper generates a fresh one for every insert.
		return new RefreshTokenRecord(
				UUID.randomUUID(),
				generation.userId().value(),
				generation.id().value(),
				generation.tokenHash().value(),
				generation.consumedAt(),
				generation.expiresAt(),
				generation.createdAt(),
				generation.updatedAt());
	}

	@Override
	public Session toDomain(RefreshTokenRecord record) {
		return new Session(
				SessionId.of(record.sessionId()),
				UserId.of(record.userId()),
				new TokenHash(record.tokenHash()),
				record.expiresAt(),
				record.consumedAt(),
				record.createdAt(),
				record.updatedAt());
	}
}
