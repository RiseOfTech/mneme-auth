package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultLoginUseCase implements LoginUseCase {

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final TokenSigner tokenSigner;
	private final SessionRepository sessionRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final Clock clock;
	private final Duration idleWindow;

	public DefaultLoginUseCase(
			UserRepository userRepository,
			PasswordHasher passwordHasher,
			TokenSigner tokenSigner,
			SessionRepository sessionRepository,
			RefreshTokenGenerator refreshTokenGenerator,
			TokenHasher tokenHasher,
			Clock clock,
			Duration idleWindow) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.tokenSigner = tokenSigner;
		this.sessionRepository = sessionRepository;
		this.refreshTokenGenerator = refreshTokenGenerator;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
		this.idleWindow = idleWindow;
	}

	@Override
	public AuthTokens login(String email, String rawPassword) {
		Optional<User> user = userRepository.findByEmail(new Email(email));

		// Unknown email and wrong password must be indistinguishable (anti-enumeration):
		// exactly one throw site for both branches, no detail about which one failed.
		if (user.isEmpty() || !passwordHasher.matches(rawPassword, user.get().passwordHash())) {
			throw new InvalidCredentialsException();
		}

		Instant now = clock.instant();
		RawRefreshToken rawRefreshToken = refreshTokenGenerator.generate();
		TokenHash tokenHash = tokenHasher.hash(rawRefreshToken);
		Session session = Session.open(user.get().id(), tokenHash, now, idleWindow);
		sessionRepository.save(session);

		String accessToken = tokenSigner.sign(user.get().id(), now);
		return new AuthTokens(accessToken, rawRefreshToken);
	}
}
