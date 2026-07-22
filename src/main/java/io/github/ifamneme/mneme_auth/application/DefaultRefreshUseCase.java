package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;

public class DefaultRefreshUseCase implements RefreshUseCase {

	private final SessionRepository sessionRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final TokenSigner tokenSigner;
	private final Clock clock;
	private final Duration idleWindow;

	public DefaultRefreshUseCase(
			SessionRepository sessionRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			TokenHasher tokenHasher,
			TokenSigner tokenSigner,
			Clock clock,
			Duration idleWindow) {
		this.sessionRepository = sessionRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.tokenHasher = tokenHasher;
		this.tokenSigner = tokenSigner;
		this.clock = clock;
		this.idleWindow = idleWindow;
	}

	// Reuse/idle-lapse both revoke the lineage and then throw InvalidRefreshTokenException to
	// reject the request; @Transactional's default policy rolls back on any unchecked
	// exception, which would silently undo that very revocation. noRollbackFor keeps the
	// revoke committed while the rejection still propagates to the caller unchanged.
	@Override
	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public AuthTokens refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		TokenHash presentedHash = tokenHasher.hash(new RawRefreshToken(rawRefreshToken));

		boolean won = sessionRepository.consumeIfLive(presentedHash, now);
		Optional<Session> presented = sessionRepository.findByTokenHash(presentedHash);

		if (won) {
			return rotate(presented.orElseThrow(() -> new IllegalStateException("winning consume left no row")), now);
		}

		if (presented.isEmpty()) {
			throw new InvalidRefreshTokenException();
		}
		Session existing = presented.get();
		if (!existing.expiresAt().isAfter(now)) {
			sessionRepository.revokeLineage(existing.id(), now);
			throw new InvalidRefreshTokenException();
		}
		if (existing.consumedAt() != null) {
			sessionRepository.revokeLineage(existing.id(), now);
			throw new InvalidRefreshTokenException();
		}
		throw new IllegalStateException("consume failed but generation appears live");
	}

	private AuthTokens rotate(Session consumed, Instant now) {
		RawRefreshToken nextRaw = refreshTokenGenerator.generate();
		Session next = consumed.rotate(tokenHasher.hash(nextRaw), now, idleWindow);
		sessionRepository.save(next);
		String accessToken = tokenSigner.sign(consumed.userId(), now);
		return new AuthTokens(accessToken, nextRaw);
	}
}
