package io.github.ifamneme.mneme_auth.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

@RequestMapping("/v1/auth")
public interface AuthApi {

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	ResponseEntity<AccessTokenResponse> register(@Valid @RequestBody RegisterRequest request);

	@PostMapping("/login")
	ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request);

	@PostMapping("/refresh")
	ResponseEntity<AccessTokenResponse> refresh(
			@CookieValue(name = "${mneme.auth.refresh.cookie-name}", required = false) String refreshToken);

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	ResponseEntity<Void> logout(
			@CookieValue(name = "${mneme.auth.refresh.cookie-name}", required = false) String refreshToken);
}
