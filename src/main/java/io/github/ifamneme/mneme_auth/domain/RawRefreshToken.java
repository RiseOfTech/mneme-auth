package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;

public record RawRefreshToken(String value) {

	public RawRefreshToken {
		Objects.requireNonNull(value, "value must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
	}
}
