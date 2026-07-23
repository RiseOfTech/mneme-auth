package io.github.ifamneme.mneme_auth.infrastructure.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;

@RequestMapping("/v1/profile")
public interface ProfileApi {

	@GetMapping
	ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt);

	@PutMapping
	ResponseEntity<ProfileResponse> updateProfile(
			@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request);
}
