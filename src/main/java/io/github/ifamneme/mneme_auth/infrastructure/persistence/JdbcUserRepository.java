package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

@Repository
public class JdbcUserRepository implements UserRepository {

	private final SpringDataUserRepository springDataUserRepository;
	private final UserMapper userMapper;

	public JdbcUserRepository(SpringDataUserRepository springDataUserRepository, UserMapper userMapper) {
		this.springDataUserRepository = springDataUserRepository;
		this.userMapper = userMapper;
	}

	@Override
	public User save(User user) {
		boolean isNew = !springDataUserRepository.existsById(user.id().value());
		UserRecord saved = springDataUserRepository.save(userMapper.toRecord(user, isNew));
		return userMapper.toDomain(saved);
	}

	@Override
	public Optional<User> findByEmail(Email email) {
		return springDataUserRepository.findByEmail(email.value()).map(userMapper::toDomain);
	}

	@Override
	public Optional<User> findById(UserId id) {
		return springDataUserRepository.findById(id.value()).map(userMapper::toDomain);
	}

	@Override
	public boolean existsByEmail(Email email) {
		return springDataUserRepository.existsByEmail(email.value());
	}
}
