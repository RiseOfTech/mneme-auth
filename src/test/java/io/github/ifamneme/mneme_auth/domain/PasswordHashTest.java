package io.github.ifamneme.mneme_auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PasswordHashTest {

	@Test
	void preservesEncodedPrefix() {
		PasswordHash hash = new PasswordHash("{bcrypt}$2a$12$abcdefghijklmnopqrstuv");

		assertThat(hash.value()).startsWith("{bcrypt}");
	}

	@Test
	void rejectsBlankValue() {
		assertThrows(IllegalArgumentException.class, () -> new PasswordHash("   "));
	}
}
