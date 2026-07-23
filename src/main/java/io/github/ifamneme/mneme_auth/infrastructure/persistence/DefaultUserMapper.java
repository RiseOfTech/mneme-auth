package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import org.springframework.stereotype.Component;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;
import io.github.ifamneme.mneme_auth.domain.UserId;

@Component
class DefaultUserMapper implements UserMapper {

	@Override
	public UserRecord toRecord(User user, boolean isNew) {
		return new UserRecord(
				user.id().value(),
				user.email().value(),
				user.passwordHash().value(),
				user.nickname() == null ? null : user.nickname().value(),
				user.createdAt(),
				user.updatedAt(),
				isNew);
	}

	@Override
	public User toDomain(UserRecord record) {
		return new User(
				UserId.of(record.id()),
				new Email(record.email()),
				new PasswordHash(record.passwordHash()),
				record.nickname() == null ? null : new Nickname(record.nickname()),
				record.createdAt(),
				record.updatedAt());
	}
}
