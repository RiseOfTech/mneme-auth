package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultRegisterUseCase implements RegisterUseCase {

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final TokenSigner tokenSigner;
	private final Clock clock;

	public DefaultRegisterUseCase(
			UserRepository userRepository, PasswordHasher passwordHasher, TokenSigner tokenSigner, Clock clock) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.tokenSigner = tokenSigner;
		this.clock = clock;
	}

	@Override
	public String register(String email, String rawPassword) {
		Email normalisedEmail = new Email(email);
		if (userRepository.existsByEmail(normalisedEmail)) {
			throw new EmailAlreadyExistsException();
		}

		PasswordHash passwordHash = passwordHasher.hash(rawPassword);
		User user = User.register(UserId.generate(), normalisedEmail, passwordHash, clock.instant());
		User saved = userRepository.save(user);

		return tokenSigner.sign(saved.id(), clock.instant());
	}
}
