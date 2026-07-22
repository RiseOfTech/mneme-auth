package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.UserId;

class DefaultRefreshUseCaseTest {

	private final Instant startInstant = Instant.parse("2026-07-19T10:00:00Z");
	private Clock clock = Clock.fixed(startInstant, ZoneOffset.UTC);
	private final Duration idleWindow = Duration.ofDays(30);
	private final FakeSessionRepository sessionRepository = new FakeSessionRepository();
	private final FakeRefreshTokenGenerator refreshTokenGenerator = new FakeRefreshTokenGenerator();
	private final FakeTokenHasher tokenHasher = new FakeTokenHasher();
	private final FakeTokenSigner tokenSigner = new FakeTokenSigner();

	private DefaultRefreshUseCase useCase() {
		return new DefaultRefreshUseCase(sessionRepository, refreshTokenGenerator, tokenHasher, tokenSigner, clock, idleWindow);
	}

	private Session openSession(UserId userId, RawRefreshToken rawToken, Instant now) {
		Session session = Session.open(userId, tokenHasher.hash(rawToken), now, idleWindow);
		sessionRepository.save(session);
		return session;
	}

	@Test
	void happyPathRotatesTheGenerationWithSlidingExpiry() {
		UserId userId = UserId.generate();
		RawRefreshToken firstToken = new RawRefreshToken("first-raw-token");
		Session opened = openSession(userId, firstToken, startInstant);

		AuthTokens tokens = useCase().refresh(firstToken.value());

		// old generation is now consumed
		Session oldGeneration =
				sessionRepository.findByTokenHash(tokenHasher.hash(firstToken)).orElseThrow();
		assertThat(oldGeneration.consumedAt()).isEqualTo(startInstant);

		// new generation exists, same lineage, not consumed, sliding expiry
		Session newGeneration = sessionRepository
				.findByTokenHash(tokenHasher.hash(tokens.refreshToken()))
				.orElseThrow();
		assertThat(newGeneration.id()).isEqualTo(opened.id());
		assertThat(newGeneration.consumedAt()).isNull();
		assertThat(newGeneration.expiresAt()).isEqualTo(startInstant.plus(idleWindow));
		assertThat(tokens.accessToken()).isEqualTo(tokenSigner.sign(userId, startInstant));
	}

	@Test
	void reuseOfAnAlreadyConsumedTokenRevokesTheWholeLineageAndRejects() {
		UserId userId = UserId.generate();
		RawRefreshToken firstToken = new RawRefreshToken("first-raw-token");
		openSession(userId, firstToken, startInstant);

		// first refresh consumes firstToken and issues a child
		AuthTokens firstRefresh = useCase().refresh(firstToken.value());

		// replay of the now-consumed firstToken
		assertThrows(InvalidRefreshTokenException.class, () -> useCase().refresh(firstToken.value()));

		assertThat(sessionRepository.revokeCalls()).isEqualTo(1);
		// the lineage's most recent (child) generation is also dead now
		assertThrows(
				InvalidRefreshTokenException.class,
				() -> useCase().refresh(firstRefresh.refreshToken().value()));
	}

	@Test
	void idleLapsePresentingAnExpiredTokenRevokesTheLineageAndRejects() {
		UserId userId = UserId.generate();
		RawRefreshToken token = new RawRefreshToken("stale-raw-token");
		Instant openedAt = startInstant.minus(idleWindow).minusSeconds(60);
		openSession(userId, token, openedAt);

		assertThrows(InvalidRefreshTokenException.class, () -> useCase().refresh(token.value()));

		assertThat(sessionRepository.revokeCalls()).isEqualTo(1);
	}

	@Test
	void unknownTokenIsRejectedAndRevokesNothing() {
		assertThrows(InvalidRefreshTokenException.class, () -> useCase().refresh("never-issued-token"));

		assertThat(sessionRepository.revokeCalls()).isEqualTo(0);
	}

	@Test
	void reuseOnOneLineageDoesNotAffectAnotherSessionLineage() {
		UserId userId = UserId.generate();
		RawRefreshToken tokenA = new RawRefreshToken("session-a-token");
		RawRefreshToken tokenB = new RawRefreshToken("session-b-token");
		openSession(userId, tokenA, startInstant);
		openSession(userId, tokenB, startInstant);

		// consume A once, then replay it -> reuse -> revoke A only
		useCase().refresh(tokenA.value());
		assertThrows(InvalidRefreshTokenException.class, () -> useCase().refresh(tokenA.value()));

		// session B is untouched and still refreshes successfully
		AuthTokens refreshedB = useCase().refresh(tokenB.value());
		assertThat(refreshedB.accessToken()).isNotBlank();
		TokenHash newBHash = tokenHasher.hash(refreshedB.refreshToken());
		assertThat(sessionRepository.findByTokenHash(newBHash)).isPresent();
	}
}
