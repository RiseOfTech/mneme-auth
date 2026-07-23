package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.ifamneme.mneme_auth.PostgresIntegrationTestBase;
import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcUserRepositoryIT extends PostgresIntegrationTestBase {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndFindsAUserByEmail() {
		User user = User.register(
				UserId.generate(),
				new Email("integration@example.com"),
				new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));

		userRepository.save(user);

		assertThat(userRepository.existsByEmail(new Email("integration@example.com"))).isTrue();
		User found = userRepository.findByEmail(new Email("integration@example.com")).orElseThrow();
		assertThat(found.id()).isEqualTo(user.id());
		assertThat(found.passwordHash()).isEqualTo(user.passwordHash());
	}

	@Test
	void unknownEmailIsNotFound() {
		assertThat(userRepository.existsByEmail(new Email("nobody@example.com"))).isFalse();
		assertThat(userRepository.findByEmail(new Email("nobody@example.com"))).isEmpty();
	}

	@Test
	void findsAUserById() {
		User user = User.register(
				UserId.generate(),
				new Email("by-id@example.com"),
				new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));
		userRepository.save(user);

		User found = userRepository.findById(user.id()).orElseThrow();
		assertThat(found.id()).isEqualTo(user.id());
		assertThat(found.nickname()).isNull();
	}

	@Test
	void unknownIdIsNotFound() {
		assertThat(userRepository.findById(UserId.generate())).isEmpty();
	}

	@Test
	void savingAnExistingUserUpdatesTheRowInPlaceRatherThanInserting() {
		Instant createdAt = Instant.parse("2026-07-19T10:00:00Z");
		User user = User.register(
				UserId.generate(), new Email("update@example.com"), new PasswordHash("{bcrypt}$2a$12$hash"), createdAt);
		userRepository.save(user);

		Instant updatedAt = Instant.parse("2026-07-19T11:00:00Z");
		User updated = user.changeNickname(new Nickname("Alice"), updatedAt);
		userRepository.save(updated);

		User found = userRepository.findById(user.id()).orElseThrow();
		assertThat(found.nickname()).isEqualTo(new Nickname("Alice"));
		assertThat(found.updatedAt()).isEqualTo(updatedAt);
		assertThat(found.createdAt()).isEqualTo(createdAt);
		assertThat(userRepository.existsByEmail(new Email("update@example.com"))).isTrue();
	}

	@Test
	void enforcesUniqueEmailConstraint() {
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		userRepository.save(User.register(
				UserId.generate(), new Email("dup@example.com"), new PasswordHash("{bcrypt}$2a$12$hash1"), now));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> userRepository.save(User.register(
						UserId.generate(),
						new Email("dup@example.com"),
						new PasswordHash("{bcrypt}$2a$12$hash2"),
						now)));
	}
}
