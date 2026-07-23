package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import io.github.ifamneme.mneme_auth.domain.User;

interface UserMapper {

	UserRecord toRecord(User user, boolean isNew);

	User toDomain(UserRecord record);
}
