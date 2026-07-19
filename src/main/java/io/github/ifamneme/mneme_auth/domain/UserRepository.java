package io.github.ifamneme.mneme_auth.domain;

import java.util.Optional;

public interface UserRepository {

	User save(User user);

	Optional<User> findByEmail(Email email);

	boolean existsByEmail(Email email);
}
