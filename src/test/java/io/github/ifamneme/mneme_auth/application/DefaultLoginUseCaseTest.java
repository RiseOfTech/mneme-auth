package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;

class DefaultLoginUseCaseTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T10:00:00Z"), ZoneOffset.UTC);
	private final FakeUserRepository userRepository = new FakeUserRepository();
	private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
	private final FakeTokenSigner tokenSigner = new FakeTokenSigner();
	private final DefaultLoginUseCase useCase =
			new DefaultLoginUseCase(userRepository, passwordHasher, tokenSigner, clock);

	private User registerFakeUser(String email, String rawPassword) {
		User user = User.register(
				UserId.generate(), new Email(email), new PasswordHash(passwordHasher.hash(rawPassword).value()),
				clock.instant());
		return userRepository.save(user);
	}

	@Test
	void successfulLoginSignsAToken() {
		User user = registerFakeUser("known@example.com", "correct-pw");

		String accessToken = useCase.login("known@example.com", "correct-pw");

		assertThat(accessToken).isEqualTo(tokenSigner.sign(user.id(), clock.instant()));
	}

	@Test
	void unknownEmailAndWrongPasswordProduceTheIdenticalFailure() {
		registerFakeUser("known@example.com", "correct-pw");

		InvalidCredentialsException unknownEmail =
				assertThrows(InvalidCredentialsException.class, () -> useCase.login("nobody@example.com", "whatever"));
		InvalidCredentialsException wrongPassword =
				assertThrows(
						InvalidCredentialsException.class, () -> useCase.login("known@example.com", "wrong-pw"));

		assertThat(unknownEmail.getClass()).isEqualTo(wrongPassword.getClass());
		assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
	}
}
