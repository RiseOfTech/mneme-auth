package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.User;

class DefaultRegisterUseCaseTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC);
	private final FakeUserRepository userRepository = new FakeUserRepository();
	private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
	private final FakeTokenSigner tokenSigner = new FakeTokenSigner();
	private final DefaultRegisterUseCase useCase =
			new DefaultRegisterUseCase(userRepository, passwordHasher, tokenSigner, clock);

	@Test
	void happyPathHashesSavesAndSignsAToken() {
		String accessToken = useCase.register("New@Example.com", "s3cret-pw");

		assertThat(accessToken).isNotBlank();
		assertThat(userRepository.size()).isEqualTo(1);
		User saved = userRepository.findByEmail(new io.github.ifamneme.mneme_auth.domain.Email("new@example.com"))
				.orElseThrow();
		assertThat(saved.passwordHash().value()).isEqualTo("{fake}s3cret-pw");
		assertThat(saved.createdAt()).isEqualTo(saved.updatedAt());
		assertThat(accessToken).isEqualTo(tokenSigner.sign(saved.id(), clock.instant()));
	}

	@Test
	void rejectsDuplicateEmailAfterNormalisation() {
		useCase.register("foo@x.y", "password1");

		assertThrows(EmailAlreadyExistsException.class, () -> useCase.register("Foo@X.Y", "password2"));
		assertThat(userRepository.size()).isEqualTo(1);
	}
}
