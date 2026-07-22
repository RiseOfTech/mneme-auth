package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import io.github.ifamneme.mneme_auth.domain.Session;

interface RefreshTokenMapper {

	RefreshTokenRecord toRecord(Session generation);

	Session toDomain(RefreshTokenRecord record);
}
