package io.github.ifamneme.mneme_auth.infrastructure.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.github.ifamneme.mneme_auth.application.GetProfileUseCase;
import io.github.ifamneme.mneme_auth.application.ProfileView;
import io.github.ifamneme.mneme_auth.application.UpdateProfileUseCase;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.infrastructure.config.CorsProperties;
import io.github.ifamneme.mneme_auth.infrastructure.config.JwtProperties;
import io.github.ifamneme.mneme_auth.infrastructure.security.NimbusTokenSigner;

@WebMvcTest(ProfileController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({ JwtProperties.class, CorsProperties.class })
class ProfileControllerTest {

	private static final String ISSUER = "mneme-auth";
	private static final String AUDIENCE = "mneme-core";
	private static final String KID = "test-key-1";
	private static final Duration TTL = Duration.ofMinutes(15);

	private final NimbusTokenSigner realSigner = new NimbusTokenSigner(
			new JwtProperties(ISSUER, AUDIENCE, TTL, KID, new ClassPathResource("test-keys/test-private-key.pem")));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetProfileUseCase getProfileUseCase;

	@MockitoBean
	private UpdateProfileUseCase updateProfileUseCase;

	@Test
	void getProfileWithAValidTokenReturns200() throws Exception {
		UserId userId = UserId.generate();
		given(getProfileUseCase.getProfile(userId)).willReturn(new ProfileView(userId, new Nickname("Alice")));

		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer " + realSigner.sign(userId, Instant.now())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userId.value().toString()))
				.andExpect(jsonPath("$.nickname").value("Alice"));
	}

	@Test
	void getProfileWithNoNicknameReturnsNullNickname() throws Exception {
		UserId userId = UserId.generate();
		given(getProfileUseCase.getProfile(userId)).willReturn(new ProfileView(userId, null));

		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer " + realSigner.sign(userId, Instant.now())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").doesNotExist());
	}

	@Test
	void getProfileWithoutATokenReturns401() throws Exception {
		mockMvc.perform(get("/v1/profile")).andExpect(status().isUnauthorized());
	}

	@Test
	void getProfileWithAMalformedTokenReturns401() throws Exception {
		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getProfileWithAnExpiredTokenReturns401() throws Exception {
		UserId userId = UserId.generate();
		String expiredToken = realSigner.sign(userId, Instant.now().minus(Duration.ofHours(1)));

		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getProfileWithAWrongSignatureTokenReturns401() throws Exception {
		String wrongSignatureToken = signWithADifferentKey(UserId.generate());

		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer " + wrongSignatureToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getProfileWithAWrongIssuerTokenReturns401() throws Exception {
		NimbusTokenSigner wrongIssuerSigner = new NimbusTokenSigner(new JwtProperties(
				"not-mneme-auth", AUDIENCE, TTL, KID, new ClassPathResource("test-keys/test-private-key.pem")));
		String wrongIssuerToken = wrongIssuerSigner.sign(UserId.generate(), Instant.now());

		mockMvc.perform(get("/v1/profile").header("Authorization", "Bearer " + wrongIssuerToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void updateProfileWithAValidTokenAndBodyReturns200() throws Exception {
		UserId userId = UserId.generate();
		given(updateProfileUseCase.updateProfile(userId, new Nickname("Alice")))
				.willReturn(new ProfileView(userId, new Nickname("Alice")));

		mockMvc.perform(put("/v1/profile")
						.header("Authorization", "Bearer " + realSigner.sign(userId, Instant.now()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"  Alice  \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("Alice"));
	}

	@Test
	void updateProfileWithABlankNicknameReturns400ProblemJson() throws Exception {
		UserId userId = UserId.generate();

		mockMvc.perform(put("/v1/profile")
						.header("Authorization", "Bearer " + realSigner.sign(userId, Instant.now()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));
	}

	@Test
	void updateProfileWithATooLongNicknameReturns400() throws Exception {
		UserId userId = UserId.generate();
		String tooLong = "a".repeat(51);

		mockMvc.perform(put("/v1/profile")
						.header("Authorization", "Bearer " + realSigner.sign(userId, Instant.now()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"" + tooLong + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));
	}

	@Test
	void updateProfileWithoutATokenReturns401() throws Exception {
		mockMvc.perform(put("/v1/profile")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nickname\":\"Alice\"}"))
				.andExpect(status().isUnauthorized());
	}

	private static String signWithADifferentKey(UserId userId) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair otherKeyPair = generator.generateKeyPair();

		Instant now = Instant.now();
		JWSHeader header =
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(userId.value().toString())
				.issuer(ISSUER)
				.audience(AUDIENCE)
				.issueTime(java.util.Date.from(now))
				.expirationTime(java.util.Date.from(now.plus(TTL)))
				.build();
		SignedJWT signedJwt = new SignedJWT(header, claims);
		signedJwt.sign(new RSASSASigner(otherKeyPair.getPrivate()));
		return signedJwt.serialize();
	}
}
