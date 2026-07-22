package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;

public class DefaultLogoutUseCase implements LogoutUseCase {

	private final SessionRepository sessionRepository;
	private final TokenHasher tokenHasher;
	private final Clock clock;

	public DefaultLogoutUseCase(SessionRepository sessionRepository, TokenHasher tokenHasher, Clock clock) {
		this.sessionRepository = sessionRepository;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
	}

	@Override
	public void logout(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}
		TokenHash presentedHash = tokenHasher.hash(new RawRefreshToken(rawRefreshToken));
		Optional<Session> presented = sessionRepository.findByTokenHash(presentedHash);
		if (presented.isEmpty()) {
			return;
		}
		Instant now = clock.instant();
		sessionRepository.revokeLineage(presented.get().id(), now);
	}
}
