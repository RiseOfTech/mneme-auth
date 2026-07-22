package io.github.ifamneme.mneme_auth.infrastructure.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.github.ifamneme.mneme_auth.application.AuthTokens;
import io.github.ifamneme.mneme_auth.application.InvalidRefreshTokenException;
import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.RefreshUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;
import io.github.ifamneme.mneme_auth.infrastructure.config.RefreshTokenProperties;

@RestController
public class AuthController implements AuthApi {

	private static final String REFRESH_COOKIE_PATH = "/v1/auth/refresh";

	private final RegisterUseCase registerUseCase;
	private final LoginUseCase loginUseCase;
	private final RefreshUseCase refreshUseCase;
	private final RefreshTokenProperties refreshTokenProperties;

	public AuthController(
			RegisterUseCase registerUseCase,
			LoginUseCase loginUseCase,
			RefreshUseCase refreshUseCase,
			RefreshTokenProperties refreshTokenProperties) {
		this.registerUseCase = registerUseCase;
		this.loginUseCase = loginUseCase;
		this.refreshUseCase = refreshUseCase;
		this.refreshTokenProperties = refreshTokenProperties;
	}

	@Override
	public ResponseEntity<AccessTokenResponse> register(RegisterRequest request) {
		AuthTokens tokens = registerUseCase.register(request.email(), request.password());
		return withRefreshCookie(tokens, HttpStatus.CREATED);
	}

	@Override
	public ResponseEntity<AccessTokenResponse> login(LoginRequest request) {
		AuthTokens tokens = loginUseCase.login(request.email(), request.password());
		return withRefreshCookie(tokens, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<AccessTokenResponse> refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		AuthTokens tokens = refreshUseCase.refresh(refreshToken);
		return withRefreshCookie(tokens, HttpStatus.OK);
	}

	private ResponseEntity<AccessTokenResponse> withRefreshCookie(AuthTokens tokens, HttpStatus status) {
		String cookieDomain = refreshTokenProperties.cookieDomain();
		ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(
						refreshTokenProperties.cookieName(), tokens.refreshToken().value())
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(refreshTokenProperties.idleWindow());
		if (cookieDomain != null && !cookieDomain.isBlank()) {
			cookieBuilder.domain(cookieDomain);
		}
		return ResponseEntity.status(status)
				.header(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString())
				.body(new AccessTokenResponse(tokens.accessToken()));
	}
}
