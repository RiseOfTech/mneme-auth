package io.github.ifamneme.mneme_auth.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ifamneme.mneme_auth.application.DefaultLoginUseCase;
import io.github.ifamneme.mneme_auth.application.DefaultRefreshUseCase;
import io.github.ifamneme.mneme_auth.application.DefaultRegisterUseCase;
import io.github.ifamneme.mneme_auth.application.LoginUseCase;
import io.github.ifamneme.mneme_auth.application.RefreshUseCase;
import io.github.ifamneme.mneme_auth.application.RegisterUseCase;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;
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
			UserRepository userRepository,
			PasswordHasher passwordHasher,
			TokenSigner tokenSigner,
			SessionRepository sessionRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			TokenHasher tokenHasher,
			Clock clock,
			RefreshTokenProperties refreshTokenProperties) {
		return new DefaultRegisterUseCase(
				userRepository,
				passwordHasher,
				tokenSigner,
				sessionRepository,
				refreshTokenGenerator,
				tokenHasher,
				clock,
				refreshTokenProperties.idleWindow());
	}

	@Bean
	LoginUseCase loginUseCase(
			UserRepository userRepository,
			PasswordHasher passwordHasher,
			TokenSigner tokenSigner,
			SessionRepository sessionRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			TokenHasher tokenHasher,
			Clock clock,
			RefreshTokenProperties refreshTokenProperties) {
		return new DefaultLoginUseCase(
				userRepository,
				passwordHasher,
				tokenSigner,
				sessionRepository,
				refreshTokenGenerator,
				tokenHasher,
				clock,
				refreshTokenProperties.idleWindow());
	}

	@Bean
	RefreshUseCase refreshUseCase(
			SessionRepository sessionRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			TokenHasher tokenHasher,
			TokenSigner tokenSigner,
			Clock clock,
			RefreshTokenProperties refreshTokenProperties) {
		return new DefaultRefreshUseCase(
				sessionRepository,
				refreshTokenGenerator,
				tokenHasher,
				tokenSigner,
				clock,
				refreshTokenProperties.idleWindow());
	}
}
