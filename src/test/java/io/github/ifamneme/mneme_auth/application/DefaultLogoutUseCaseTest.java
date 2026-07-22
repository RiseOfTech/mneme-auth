package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionId;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.UserId;

class DefaultLogoutUseCaseTest {

	private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

	private final FakeSessionRepository sessionRepository = new FakeSessionRepository();
	private final FakeTokenHasher tokenHasher = new FakeTokenHasher();
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final DefaultLogoutUseCase logoutUseCase = new DefaultLogoutUseCase(sessionRepository, tokenHasher, clock);

	@Test
	void revokesTheLineageOfTheSessionResolvedFromTheToken() {
		TokenHash hash = tokenHasher.hash(new RawRefreshToken("raw-token"));
		Session live = new Session(
				SessionId.generate(), UserId.generate(), hash, NOW.plus(Duration.ofDays(30)), null, NOW, NOW);
		sessionRepository.save(live);

		logoutUseCase.logout("raw-token");

		assertThat(sessionRepository.revokeCalls()).isEqualTo(1);
		assertThat(sessionRepository.isLive(hash, NOW)).isFalse();
	}

	@Test
	void anAbsentCookieIsANoOpAndDoesNotThrow() {
		logoutUseCase.logout(null);
		logoutUseCase.logout("");
		logoutUseCase.logout("   ");

		assertThat(sessionRepository.revokeCalls()).isZero();
	}

	@Test
	void anUnknownTokenIsANoOp() {
		logoutUseCase.logout("never-issued-token");

		assertThat(sessionRepository.revokeCalls()).isZero();
	}

	@Test
	void aConsumedTokenStillRevokesItsLineageViaSessionIdWithoutReuseDetection() {
		TokenHash hash = tokenHasher.hash(new RawRefreshToken("consumed-token"));
		Session consumed = new Session(
				SessionId.generate(), UserId.generate(), hash, NOW.plus(Duration.ofDays(30)), NOW.minusSeconds(60), NOW, NOW);
		sessionRepository.save(consumed);

		logoutUseCase.logout("consumed-token");

		assertThat(sessionRepository.revokeCalls()).isEqualTo(1);
	}

	@Test
	void anExpiredTokenStillRevokesItsLineage() {
		TokenHash hash = tokenHasher.hash(new RawRefreshToken("expired-token"));
		Session expired =
				new Session(SessionId.generate(), UserId.generate(), hash, NOW.minusSeconds(1), null, NOW, NOW);
		sessionRepository.save(expired);

		logoutUseCase.logout("expired-token");

		assertThat(sessionRepository.revokeCalls()).isEqualTo(1);
	}
}
