package io.github.ifamneme.mneme_auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void registrationStampsCreatedAtEqualToUpdatedAtFromTheClock() {
		Instant fixedInstant = Instant.parse("2026-07-19T10:15:30Z");
		Clock clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

		User user = User.register(
				UserId.generate(), new Email("a@b.com"), new PasswordHash("{bcrypt}$2a$12$hash"), clock.instant());

		assertThat(user.createdAt()).isEqualTo(fixedInstant);
		assertThat(user.updatedAt()).isEqualTo(fixedInstant);
		assertThat(user.createdAt()).isEqualTo(user.updatedAt());
		assertThat(user.nickname()).isNull();
	}

	@Test
	void changeNicknameAdvancesUpdatedAtButNotCreatedAt() {
		Instant registeredAt = Instant.parse("2026-07-19T10:15:30Z");
		User user = User.register(
				UserId.generate(), new Email("a@b.com"), new PasswordHash("{bcrypt}$2a$12$hash"), registeredAt);

		Instant changedAt = Instant.parse("2026-07-20T09:00:00Z");
		User changed = user.changeNickname(new Nickname("Alice"), changedAt);

		assertThat(changed.nickname()).isEqualTo(new Nickname("Alice"));
		assertThat(changed.updatedAt()).isEqualTo(changedAt);
		assertThat(changed.createdAt()).isEqualTo(registeredAt);
		assertThat(changed.id()).isEqualTo(user.id());
	}
}
