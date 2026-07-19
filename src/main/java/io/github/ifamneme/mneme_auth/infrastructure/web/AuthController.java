package io.github.ifamneme.mneme_auth.infrastructure.web;

import org.springframework.web.bind.annotation.RestController;

import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;

@RestController
public class AuthController implements AuthApi {

	private final RegisterUseCase registerUseCase;
	private final LoginUseCase loginUseCase;

	public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase) {
		this.registerUseCase = registerUseCase;
		this.loginUseCase = loginUseCase;
	}

	@Override
	public AccessTokenResponse register(RegisterRequest request) {
		String accessToken = registerUseCase.register(request.email(), request.password());
		return new AccessTokenResponse(accessToken);
	}

	@Override
	public AccessTokenResponse login(LoginRequest request) {
		String accessToken = loginUseCase.login(request.email(), request.password());
		return new AccessTokenResponse(accessToken);
	}
}
