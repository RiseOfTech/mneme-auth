package io.github.ifamneme.mneme_auth.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.infrastructure.config.JwtProperties;

@Component
public class NimbusTokenSigner implements TokenSigner {

	private final JwtProperties properties;
	private final JwtEncoder jwtEncoder;

	public NimbusTokenSigner(JwtProperties properties) {
		this.properties = properties;
		RSAPrivateKey privateKey = readPrivateKey(properties);
		RSAPublicKey publicKey = derivePublicKey(privateKey);
		RSAKey rsaKey = new RSAKey.Builder(publicKey)
				.privateKey(privateKey)
				.keyID(properties.kid())
				.build();
		ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
		this.jwtEncoder = new NimbusJwtEncoder(jwkSource);
	}

	@Override
	public String sign(UserId userId, Instant issuedAt) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
				.type("JWT")
				.keyId(properties.kid())
				.build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(userId.value().toString())
				.issuer(properties.issuer())
				.audience(List.of(properties.audience()))
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(properties.ttl()))
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static RSAPrivateKey readPrivateKey(JwtProperties properties) {
		try (InputStream in = properties.privateKeyLocation().getInputStream()) {
			String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
			String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
					.replaceAll("\\s", "");
			byte[] der = Base64.getDecoder().decode(base64);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (IOException | GeneralSecurityException e) {
			throw new IllegalStateException("failed to load RS256 private key", e);
		}
	}

	private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
		RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
		try {
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			RSAPublicKeySpec publicKeySpec =
					new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
			return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("failed to derive RS256 public key", e);
		}
	}
}
