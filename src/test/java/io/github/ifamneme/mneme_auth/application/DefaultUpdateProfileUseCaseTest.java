package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;

class DefaultUpdateProfileUseCaseTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC);
	private final FakeUserRepository userRepository = new FakeUserRepository();
	private final DefaultUpdateProfileUseCase useCase = new DefaultUpdateProfileUseCase(userRepository, clock);

	@Test
	void changesTheNicknameAndSavesTheUser() {
		User user = User.register(
				UserId.generate(),
				new Email("a@b.com"),
				new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));
		userRepository.save(user);

		ProfileView profile = useCase.updateProfile(user.id(), new Nickname("Alice"));

		assertThat(profile.userId()).isEqualTo(user.id());
		assertThat(profile.nickname()).isEqualTo(new Nickname("Alice"));
		User saved = userRepository.findById(user.id()).orElseThrow();
		assertThat(saved.nickname()).isEqualTo(new Nickname("Alice"));
		assertThat(saved.updatedAt()).isEqualTo(clock.instant());
	}
}
