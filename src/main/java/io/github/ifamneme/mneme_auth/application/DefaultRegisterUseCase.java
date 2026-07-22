package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.PasswordHasher;
import io.github.ifamneme.mneme_auth.domain.RawRefreshToken;
import io.github.ifamneme.mneme_auth.domain.RefreshTokenGenerator;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.TokenHasher;
import io.github.ifamneme.mneme_auth.domain.TokenSigner;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultRegisterUseCase implements RegisterUseCase {

	private final UserRepository userRepository;
	private final PasswordHasher passwordHasher;
	private final TokenSigner tokenSigner;
	private final SessionRepository sessionRepository;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final Clock clock;
	private final Duration idleWindow;

	public DefaultRegisterUseCase(
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
	@Transactional
	public AuthTokens register(String email, String rawPassword) {
		Email normalisedEmail = new Email(email);
		if (userRepository.existsByEmail(normalisedEmail)) {
			throw new EmailAlreadyExistsException();
		}

		Instant now = clock.instant();
		PasswordHash passwordHash = passwordHasher.hash(rawPassword);
		User user = User.register(UserId.generate(), normalisedEmail, passwordHash, now);
		User saved = userRepository.save(user);

		RawRefreshToken rawRefreshToken = refreshTokenGenerator.generate();
		TokenHash tokenHash = tokenHasher.hash(rawRefreshToken);
		Session session = Session.open(saved.id(), tokenHash, now, idleWindow);
		sessionRepository.save(session);

		String accessToken = tokenSigner.sign(saved.id(), now);
		return new AuthTokens(accessToken, rawRefreshToken);
	}
}
