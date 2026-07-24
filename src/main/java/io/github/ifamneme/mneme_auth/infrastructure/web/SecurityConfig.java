package io.github.ifamneme.mneme_auth.infrastructure.web;

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
import java.util.Base64;
import java.util.List;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import io.github.ifamneme.mneme_auth.infrastructure.config.CorsProperties;
import io.github.ifamneme.mneme_auth.infrastructure.config.JwtProperties;

@Configuration
@EnableWebSecurity
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http, JwtDecoder jwtDecoder, CorsConfigurationSource corsConfigurationSource)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						// Boot forwards internally to /error whenever an unhandled MVC exception
						// (bad content-type, malformed body, wrong HTTP method, ...) sets an error
						// status on a request to a permitAll path. That forwarded ERROR dispatch
						// re-enters this same chain; without this, it falls through to
						// anyRequest().authenticated() and turns the real 400/404/405 into a bare
						// 401, making /v1/auth/** "public" only for requests that never throw.
						.dispatcherTypeMatchers(DispatcherType.ERROR)
						.permitAll()
						// Preflight requests carry no credentials, so if they fell through to
						// anyRequest().authenticated() below they'd get a 401 and the browser
						// would never send the real request.
						.requestMatchers(CorsUtils::isPreFlightRequest)
						.permitAll()
						.requestMatchers("/v1/auth/**", "/actuator/health")
						.permitAll()
						.anyRequest()
						.authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/v1/**", configuration);
		return source;
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		RSAPublicKey publicKey = derivePublicKey(properties.privateKeyLocation());
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
		return decoder;
	}

	// Derives the public key from the same private-key resource NimbusTokenSigner already
	// loads, rather than a separate public-key file: the decoder's key must match the
	// signer's, and this needs no new deployment secret. Duplicated (not shared) with
	// NimbusTokenSigner's identical derivation so that class stays untouched by this slice.
	private static RSAPublicKey derivePublicKey(Resource privateKeyLocation) {
		try (InputStream in = privateKeyLocation.getInputStream()) {
			String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
			String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
			byte[] der = Base64.getDecoder().decode(base64);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
			RSAPrivateCrtKey crtKey = (RSAPrivateCrtKey) privateKey;
			RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
			return (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
		} catch (IOException | GeneralSecurityException e) {
			throw new IllegalStateException("failed to derive RS256 public key for the JWT decoder", e);
		}
	}
}
