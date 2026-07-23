package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

public class DefaultGetProfileUseCase implements GetProfileUseCase {

	private final UserRepository userRepository;

	public DefaultGetProfileUseCase(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public ProfileView getProfile(UserId userId) {
		User user = userRepository.findById(userId).orElseThrow();
		return new ProfileView(user.id(), user.nickname());
	}
}
