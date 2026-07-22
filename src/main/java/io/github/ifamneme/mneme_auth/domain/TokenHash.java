package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;

public record TokenHash(String value) {

	public TokenHash {
		Objects.requireNonNull(value, "value must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
	}
}
