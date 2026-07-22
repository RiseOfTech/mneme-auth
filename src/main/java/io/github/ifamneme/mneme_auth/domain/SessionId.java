package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;
import java.util.UUID;

public record SessionId(UUID value) {

	public SessionId {
		Objects.requireNonNull(value, "value must not be null");
	}

	public static SessionId generate() {
		return new SessionId(UUID.randomUUID());
	}

	public static SessionId of(UUID value) {
		return new SessionId(value);
	}
}
