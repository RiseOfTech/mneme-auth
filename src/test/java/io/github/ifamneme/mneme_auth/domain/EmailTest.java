package io.github.ifamneme.mneme_auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmailTest {

	@Test
	void normalisesCaseAndWhitespace() {
		Email email = new Email("  Foo@X.Y  ");

		assertThat(email.value()).isEqualTo("foo@x.y");
	}

	@Test
	void treatsCaseAndWhitespaceVariantsAsEqual() {
		assertThat(new Email("Foo@X.Y")).isEqualTo(new Email(" foo@x.y "));
	}

	@Test
	void rejectsEmptyValue() {
		assertThrows(IllegalArgumentException.class, () -> new Email("   "));
	}

	@Test
	void rejectsValueWithoutAtSign() {
		assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
	}

	@Test
	void rejectsValueLongerThan254Characters() {
		String local = "a".repeat(250);
		assertThrows(IllegalArgumentException.class, () -> new Email(local + "@b.co"));
	}
}
