package io.github.ifamneme.mneme_auth.infrastructure.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import io.github.ifamneme.mneme_auth.application.AuthTokens;
import io.github.ifamneme.mneme_auth.application.EmailAlreadyExistsException;
import io.github.ifamneme.mneme_auth.application.InvalidCredentialsException;
import io.github.ifamneme.mneme_auth.application.InvalidRefreshTokenException;
import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.LogoutUseCase;
import io.github.ifamneme.mneme_auth.application.RefreshUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;
import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.infrastructure.config.RefreshTokenProperties;

@WebMvcTest(AuthController.class)
@EnableConfigurationProperties(RefreshTokenProperties.class)
class AuthControllerTest {

	private static final String COOKIE_NAME = "mneme_refresh";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterUseCase registerUseCase;

	@MockitoBean
	private LoginUseCase loginUseCase;

	@MockitoBean
	private RefreshUseCase refreshUseCase;

	@MockitoBean
	private LogoutUseCase logoutUseCase;

	@Test
	void registerReturns201WithAccessTokenAndSetsRefreshCookie() throws Exception {
		given(registerUseCase.register(anyString(), anyString()))
				.willReturn(new AuthTokens("access-token-123", new RawRefreshToken("raw-refresh-123")));

		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").value("access-token-123"))
				.andExpect(cookie().value(COOKIE_NAME, "raw-refresh-123"))
				.andExpect(cookie().httpOnly(COOKIE_NAME, true))
				.andExpect(cookie().secure(COOKIE_NAME, true))
				.andExpect(cookie().path(COOKIE_NAME, "/v1/auth"));
	}

	@Test
	void registerWithInvalidEmailReturns400ProblemJson() throws Exception {
		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\",\"password\":\"password1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.code").value("validation_failed"));
	}

	@Test
	void registerWithShortPasswordReturns400() throws Exception {
		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"short\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));
	}

	@Test
	void registerWithTakenEmailReturns409ProblemJson() throws Exception {
		given(registerUseCase.register(anyString(), anyString())).willThrow(new EmailAlreadyExistsException());

		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password1\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.code").value("email_taken"));
	}

	@Test
	void loginReturns200WithAccessTokenAndSetsRefreshCookie() throws Exception {
		given(loginUseCase.login(anyString(), anyString()))
				.willReturn(new AuthTokens("access-token-456", new RawRefreshToken("raw-refresh-456")));

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token-456"))
				.andExpect(cookie().value(COOKIE_NAME, "raw-refresh-456"));
	}

	@Test
	void loginWithInvalidCredentialsReturns401ProblemJson() throws Exception {
		given(loginUseCase.login(anyString(), anyString())).willThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"wrongpassword\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	@Test
	void refreshWithValidCookieReturns200WithNewAccessTokenAndRotatedCookie() throws Exception {
		given(refreshUseCase.refresh("incoming-raw-token"))
				.willReturn(new AuthTokens("access-token-789", new RawRefreshToken("rotated-raw-token")));

		mockMvc.perform(post("/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, "incoming-raw-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token-789"))
				.andExpect(cookie().value(COOKIE_NAME, "rotated-raw-token"));
	}

	@Test
	void refreshWithoutACookieReturns401ProblemJsonAndNeverCallsTheUseCase() throws Exception {
		mockMvc.perform(post("/v1/auth/refresh"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("invalid_refresh"));

		verifyNoInteractions(refreshUseCase);
	}

	@Test
	void refreshWithAnInvalidTokenReturns401ProblemJson() throws Exception {
		given(refreshUseCase.refresh(anyString())).willThrow(new InvalidRefreshTokenException());

		mockMvc.perform(post("/v1/auth/refresh").cookie(new Cookie(COOKIE_NAME, "stale-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("invalid_refresh"));
	}

	@Test
	void logoutWithAValidCookieReturns204WithAClearedCookieAndInvokesTheUseCase() throws Exception {
		mockMvc.perform(post("/v1/auth/logout").cookie(new Cookie(COOKIE_NAME, "some-raw-token")))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""))
				.andExpect(cookie().value(COOKIE_NAME, ""))
				.andExpect(cookie().maxAge(COOKIE_NAME, 0))
				.andExpect(cookie().path(COOKIE_NAME, "/v1/auth"))
				.andExpect(cookie().httpOnly(COOKIE_NAME, true))
				.andExpect(cookie().secure(COOKIE_NAME, true));

		verify(logoutUseCase).logout("some-raw-token");
	}

	@Test
	void logoutWithoutACookieStillReturns204WithAClearedCookie() throws Exception {
		mockMvc.perform(post("/v1/auth/logout"))
				.andExpect(status().isNoContent())
				.andExpect(cookie().value(COOKIE_NAME, ""))
				.andExpect(cookie().maxAge(COOKIE_NAME, 0));

		verify(logoutUseCase).logout(null);
	}
}
