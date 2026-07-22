package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.User;

class DefaultRegisterUseCaseTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC);
	private final Duration idleWindow = Duration.ofDays(30);
	private final FakeUserRepository userRepository = new FakeUserRepository();
	private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
	private final FakeTokenSigner tokenSigner = new FakeTokenSigner();
	private final FakeSessionRepository sessionRepository = new FakeSessionRepository();
	private final FakeRefreshTokenGenerator refreshTokenGenerator = new FakeRefreshTokenGenerator();
	private final FakeTokenHasher tokenHasher = new FakeTokenHasher();
	private final DefaultRegisterUseCase useCase = new DefaultRegisterUseCase(
			userRepository,
			passwordHasher,
			tokenSigner,
			sessionRepository,
			refreshTokenGenerator,
			tokenHasher,
			clock,
			idleWindow);

	@Test
	void happyPathHashesSavesAndSignsAToken() {
		AuthTokens tokens = useCase.register("New@Example.com", "s3cret-pw");

		assertThat(tokens.accessToken()).isNotBlank();
		assertThat(userRepository.size()).isEqualTo(1);
		User saved = userRepository.findByEmail(new io.github.ifamneme.mneme_auth.domain.Email("new@example.com"))
				.orElseThrow();
		assertThat(saved.passwordHash().value()).isEqualTo("{fake}s3cret-pw");
		assertThat(saved.createdAt()).isEqualTo(saved.updatedAt());
		assertThat(tokens.accessToken()).isEqualTo(tokenSigner.sign(saved.id(), clock.instant()));
	}

	@Test
	void rejectsDuplicateEmailAfterNormalisation() {
		useCase.register("foo@x.y", "password1");

		assertThrows(EmailAlreadyExistsException.class, () -> useCase.register("Foo@X.Y", "password2"));
		assertThat(userRepository.size()).isEqualTo(1);
	}

	@Test
	void opensASessionAndIssuesTheFirstGeneration() {
		AuthTokens tokens = useCase.register("new@example.com", "s3cret-pw");
		User saved =
				userRepository.findByEmail(new io.github.ifamneme.mneme_auth.domain.Email("new@example.com")).orElseThrow();

		Session session = sessionRepository
				.findByTokenHash(tokenHasher.hash(tokens.refreshToken()))
				.orElseThrow();

		assertThat(session.userId()).isEqualTo(saved.id());
		assertThat(session.consumedAt()).isNull();
		assertThat(session.expiresAt()).isEqualTo(clock.instant().plus(idleWindow));
	}
}
