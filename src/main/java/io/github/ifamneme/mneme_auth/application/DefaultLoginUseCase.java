package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;
import java.util.Optional;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultLoginUseCase implements LoginUseCase {

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final TokenSigner tokenSigner;
	private final Clock clock;

	public DefaultLoginUseCase(
			UserRepository userRepository, PasswordHasher passwordHasher, TokenSigner tokenSigner, Clock clock) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.tokenSigner = tokenSigner;
		this.clock = clock;
	}

	@Override
	public String login(String email, String rawPassword) {
		Optional<User> user = userRepository.findByEmail(new Email(email));

		// Unknown email and wrong password must be indistinguishable (anti-enumeration):
		// exactly one throw site for both branches, no detail about which one failed.
		if (user.isEmpty() || !passwordHasher.matches(rawPassword, user.get().passwordHash())) {
			throw new InvalidCredentialsException();
		}

		return tokenSigner.sign(user.get().id(), clock.instant());
	}
}
