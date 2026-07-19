package io.github.ifamneme.mneme_auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.infrastructure.config.JwtProperties;

class NimbusTokenSignerTest {

	private static final String ISSUER = "mneme-auth";
	private static final String AUDIENCE = "mneme-core";
	private static final String KID = "test-key-1";
	private static final Duration TTL = Duration.ofMinutes(15);

	@Test
	void signsATokenVerifiableByAnIndependentlyLoadedPublicKey() throws Exception {
		JwtProperties properties = new JwtProperties(
				ISSUER, AUDIENCE, TTL, KID, new ClassPathResource("test-keys/test-private-key.pem"));
		NimbusTokenSigner signer = new NimbusTokenSigner(properties);

		UserId userId = UserId.generate();
		Instant issuedAt = Instant.parse("2026-07-19T10:00:00Z");

		String token = signer.sign(userId, issuedAt);

		// Verified with test-public-key.pem, loaded independently of NimbusTokenSigner's
		// internal derivation of the public key from the private key — this is what proves
		// the token is verifiable by a party holding only the public key (mneme-core's
		// position under ARCHITECTURE.md §10), not merely internally self-consistent.
		RSAPublicKey independentPublicKey = readIndependentTestPublicKey();
		SignedJWT signedJwt = SignedJWT.parse(token);
		assertThat(signedJwt.verify(new RSASSAVerifier(independentPublicKey))).isTrue();

		assertThat(signedJwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
		assertThat(signedJwt.getHeader().getType()).isEqualTo(JOSEObjectType.JWT);
		assertThat(signedJwt.getHeader().getKeyID()).isEqualTo(KID);

		JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
		assertThat(claims.getSubject()).isEqualTo(userId.value().toString());
		assertThat(claims.getIssuer()).isEqualTo(ISSUER);
		assertThat(claims.getAudience()).containsExactly(AUDIENCE);
		assertThat(claims.getIssueTime().toInstant()).isEqualTo(issuedAt);
		assertThat(claims.getExpirationTime().toInstant()).isEqualTo(issuedAt.plus(TTL));
	}

	private static RSAPublicKey readIndependentTestPublicKey() throws Exception {
		try (InputStream in = new ClassPathResource("test-keys/test-public-key.pem").getInputStream()) {
			String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
			String base64 =
					pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
			byte[] der = Base64.getDecoder().decode(base64);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(der));
		}
	}
}
