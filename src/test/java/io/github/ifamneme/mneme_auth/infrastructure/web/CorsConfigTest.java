package io.github.ifamneme.mneme_auth.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
class CorsConfigTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:5173";

	private static final String DISALLOWED_ORIGIN = "http://evil.example";

	private final NimbusTokenSigner realSigner = new NimbusTokenSigner(new JwtProperties(
			"mneme-auth", "mneme-core", Duration.ofMinutes(15), "test-key-1",
			new ClassPathResource("test-keys/test-private-key.pem")));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetProfileUseCase getProfileUseCase;

	@MockitoBean
	private UpdateProfileUseCase updateProfileUseCase;

	@Test
	void whenPreflightFromAllowedOrigin_returnsAllowedCorsHeaders() throws Exception {
		mockMvc.perform(options("/v1/profile").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("authorization")));
	}

	@Test
	void whenPreflightFromDisallowedOrigin_doesNotEchoOrigin() throws Exception {
		mockMvc.perform(options("/v1/profile").header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	@Test
	void whenRealCrossOriginRequestWithValidToken_succeedsWithCorsHeaders() throws Exception {
		UserId userId = UserId.generate();
		given(getProfileUseCase.getProfile(userId)).willReturn(new ProfileView(userId, new Nickname("Alice")));

		mockMvc.perform(get("/v1/profile").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + realSigner.sign(userId, Instant.now())))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	void whenOptionsPreflightWithoutAuthorizationHeader_isNotUnauthorized() throws Exception {
		// /v1/profile requires a bearer token for real requests, but the preflight sent
		// ahead of it carries no credentials at all - if it fell through to the
		// authentication filter it would get a 401 and the browser would never send the
		// real, authenticated request.
		mockMvc.perform(options("/v1/profile").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andExpect(status().isOk());
	}

}
