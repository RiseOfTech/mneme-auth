package io.github.ifamneme.mneme_auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NicknameTest {

	@Test
	void trimsSurroundingWhitespaceAndPreservesCase() {
		Nickname nickname = new Nickname("  Alice  ");

		assertThat(nickname.value()).isEqualTo("Alice");
	}

	@Test
	void rejectsABlankNickname() {
		assertThatThrownBy(() -> new Nickname("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANicknameLongerThan50Characters() {
		String tooLong = "a".repeat(51);

		assertThatThrownBy(() -> new Nickname(tooLong)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsANicknameOfExactly50Characters() {
		String exactly50 = "a".repeat(50);

		assertThat(new Nickname(exactly50).value()).isEqualTo(exactly50);
	}
}
