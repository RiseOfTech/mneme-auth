package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

	public UserId {
		Objects.requireNonNull(value, "value must not be null");
	}

	public static UserId generate() {
		return new UserId(UUID.randomUUID());
	}

	public static UserId of(UUID value) {
		return new UserId(value);
	}
}
