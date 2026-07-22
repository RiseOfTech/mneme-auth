package io.github.ifamneme.mneme_auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.TokenHash;

class Sha256TokenHasherTest {

	private final Sha256TokenHasher hasher = new Sha256TokenHasher();

	@Test
	void hashesAKnownInputToItsKnownSha256HexDigest() {
		// Known SHA-256 test vector for the ASCII string "abc", independent of this
		// implementation (NIST FIPS 180-4 example), not derived from the hasher itself.
		TokenHash hash = hasher.hash(new RawRefreshToken("abc"));

		assertThat(hash.value())
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void isDeterministicAndProducesSixtyFourHexCharacters() {
		RawRefreshToken token = new RawRefreshToken("some-raw-refresh-token");

		TokenHash first = hasher.hash(token);
		TokenHash second = hasher.hash(token);

		assertThat(first).isEqualTo(second);
		assertThat(first.value()).hasSize(64);
		assertThat(first.value()).matches("[0-9a-f]{64}");
	}
}
