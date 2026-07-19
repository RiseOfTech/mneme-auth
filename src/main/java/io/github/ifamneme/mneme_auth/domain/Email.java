package io.github.ifamneme.mneme_auth.domain;

import java.util.Locale;
import java.util.Objects;

public record Email(String value) {

	private static final int MAX_LENGTH = 254;

	public Email {
		Objects.requireNonNull(value, "value must not be null");
		value = value.strip().toLowerCase(Locale.ROOT);
		if (value.isEmpty() || !value.contains("@")) {
			throw new IllegalArgumentException("email must be non-empty and contain '@'");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("email must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
