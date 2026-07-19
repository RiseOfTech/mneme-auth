package io.github.ifamneme.mneme_auth.infrastructure.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import jakarta.validation.Valid;

@RequestMapping("/v1/auth")
public interface AuthApi {

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AccessTokenResponse register(@Valid @RequestBody RegisterRequest request);

	@PostMapping("/login")
	AccessTokenResponse login(@Valid @RequestBody LoginRequest request);
}
