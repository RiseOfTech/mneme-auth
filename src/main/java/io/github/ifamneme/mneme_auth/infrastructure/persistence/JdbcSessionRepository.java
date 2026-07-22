package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionId;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;

@Repository
public class JdbcSessionRepository implements SessionRepository {

	private final SpringDataRefreshTokenRepository springDataRefreshTokenRepository;
	private final RefreshTokenMapper refreshTokenMapper;

	public JdbcSessionRepository(
			SpringDataRefreshTokenRepository springDataRefreshTokenRepository, RefreshTokenMapper refreshTokenMapper) {
		this.springDataRefreshTokenRepository = springDataRefreshTokenRepository;
		this.refreshTokenMapper = refreshTokenMapper;
	}

	@Override
	public Session save(Session generation) {
		RefreshTokenRecord saved = springDataRefreshTokenRepository.save(refreshTokenMapper.toRecord(generation));
		return refreshTokenMapper.toDomain(saved);
	}

	@Override
	public Optional<Session> findByTokenHash(TokenHash tokenHash) {
		return springDataRefreshTokenRepository.findByTokenHash(tokenHash.value()).map(refreshTokenMapper::toDomain);
	}

	@Override
	public boolean consumeIfLive(TokenHash tokenHash, Instant now) {
		return springDataRefreshTokenRepository.consumeIfLive(tokenHash.value(), now) == 1;
	}

	@Override
	public void revokeLineage(SessionId sessionId, Instant now) {
		springDataRefreshTokenRepository.revokeLineage(sessionId.value(), now);
	}
}
