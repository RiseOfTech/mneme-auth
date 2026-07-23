package io.github.ifamneme.mneme_auth.domain;

import java.time.Instant;
import java.util.Objects;

public record User(
		UserId id, Email email, PasswordHash passwordHash, Nickname nickname, Instant createdAt, Instant updatedAt) {

	public User {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(email, "email must not be null");
		Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
		Objects.requireNonNull(updatedAt, "updatedAt must not be null");
	}

	public static User register(UserId id, Email email, PasswordHash passwordHash, Instant now) {
		return new User(id, email, passwordHash, null, now, now);
	}

	public User changeNickname(Nickname nickname, Instant now) {
		Objects.requireNonNull(nickname, "nickname must not be null");
		return new User(id, email, passwordHash, nickname, createdAt, now);
	}
}
