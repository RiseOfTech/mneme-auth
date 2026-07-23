package io.github.ifamneme.mneme_auth.domain;

import java.util.Objects;

public record Nickname(String value) {

	private static final int MAX_LENGTH = 50;

	public Nickname {
		Objects.requireNonNull(value, "value must not be null");
		value = value.strip();
		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("nickname must be between 1 and " + MAX_LENGTH + " characters");
		}
	}
}
