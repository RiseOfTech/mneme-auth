package io.github.ifamneme.mneme_auth.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

class FakeUserRepository implements UserRepository {

	private final Map<String, User> usersByEmail = new LinkedHashMap<>();

	@Override
	public User save(User user) {
		usersByEmail.put(user.email().value(), user);
		return user;
	}

	@Override
	public Optional<User> findByEmail(Email email) {
		return Optional.ofNullable(usersByEmail.get(email.value()));
	}

	@Override
	public boolean existsByEmail(Email email) {
		return usersByEmail.containsKey(email.value());
	}

	int size() {
		return usersByEmail.size();
	}
}
