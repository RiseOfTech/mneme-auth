package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;

class DefaultGetProfileUseCaseTest {

	private final FakeUserRepository userRepository = new FakeUserRepository();
	private final DefaultGetProfileUseCase useCase = new DefaultGetProfileUseCase(userRepository);

	@Test
	void returnsTheStoredProfileWithANullNicknameWhenNeverSet() {
		User user = User.register(
				UserId.generate(),
				new Email("a@b.com"),
				new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));
		userRepository.save(user);

		ProfileView profile = useCase.getProfile(user.id());

		assertThat(profile.userId()).isEqualTo(user.id());
		assertThat(profile.nickname()).isNull();
	}

	@Test
	void returnsTheStoredNicknameWhenSet() {
		User user = User.register(
				UserId.generate(),
				new Email("a@b.com"),
				new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));
		userRepository.save(user.changeNickname(new Nickname("Alice"), Instant.parse("2026-07-19T11:00:00Z")));

		ProfileView profile = useCase.getProfile(user.id());

		assertThat(profile.nickname()).isEqualTo(new Nickname("Alice"));
	}

	@Test
	void unknownUserIdIsRejected() {
		assertThrows(NoSuchElementException.class, () -> useCase.getProfile(UserId.generate()));
	}
}
