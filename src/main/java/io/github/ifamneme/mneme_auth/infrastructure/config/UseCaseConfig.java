package io.github.ifamneme.mneme_auth.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ifamneme.mneme_auth.application.DefaultLoginUseCase;
import io.github.ifamneme.mneme_auth.application.DefaultRegisterUseCase;
import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

@Configuration
class UseCaseConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	RegisterUseCase registerUseCase(
			UserRepository userRepository, PasswordHasher passwordHasher, TokenSigner tokenSigner, Clock clock) {
		return new DefaultRegisterUseCase(userRepository, passwordHasher, tokenSigner, clock);
	}

	@Bean
	LoginUseCase loginUseCase(
			UserRepository userRepository, PasswordHasher passwordHasher, TokenSigner tokenSigner, Clock clock) {
		return new DefaultLoginUseCase(userRepository, passwordHasher, tokenSigner, clock);
	}
}
