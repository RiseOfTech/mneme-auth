package io.github.ifamneme.mneme_auth.infrastructure.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.github.ifamneme.mneme_auth.application.EmailAlreadyExistsException;
import io.github.ifamneme.mneme_auth.application.InvalidCredentialsException;
import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RegisterUseCase registerUseCase;

	@MockitoBean
	private LoginUseCase loginUseCase;

	@Test
	void registerReturns201WithAccessToken() throws Exception {
		given(registerUseCase.register(anyString(), anyString())).willReturn("access-token-123");

		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").value("access-token-123"));
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
	void loginReturns200WithAccessToken() throws Exception {
		given(loginUseCase.login(anyString(), anyString())).willReturn("access-token-456");

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@b.com\",\"password\":\"password1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token-456"));
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
}
