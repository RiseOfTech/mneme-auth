package io.github.ifamneme.mneme_auth.application;

import java.time.Clock;

import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultUpdateProfileUseCase implements UpdateProfileUseCase {

	private final UserRepository userRepository;
	private final Clock clock;

	public DefaultUpdateProfileUseCase(UserRepository userRepository, Clock clock) {
		this.userRepository = userRepository;
		this.clock = clock;
	}

	@Override
	public ProfileView updateProfile(UserId userId, Nickname nickname) {
		User user = userRepository.findById(userId).orElseThrow();
		User updated = user.changeNickname(nickname, clock.instant());
		User saved = userRepository.save(updated);
		return new ProfileView(saved.id(), saved.nickname());
	}
}
