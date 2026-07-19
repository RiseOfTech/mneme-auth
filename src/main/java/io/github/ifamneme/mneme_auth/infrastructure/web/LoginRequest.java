package io.github.ifamneme.mneme_auth.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank @Pattern(regexp = ".+@.+") @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 64) String password) {}
