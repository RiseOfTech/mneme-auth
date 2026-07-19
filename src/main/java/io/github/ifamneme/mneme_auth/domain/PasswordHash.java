package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;

public record PasswordHash(String value) {

	public PasswordHash {
		Objects.requireNonNull(value, "value must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
	}
}
