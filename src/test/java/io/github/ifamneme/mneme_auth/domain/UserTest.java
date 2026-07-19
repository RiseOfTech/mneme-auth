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
	}
}
