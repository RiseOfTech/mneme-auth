package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record UserRecord(
		@Id UUID id,
		String email,
		String passwordHash,
		String nickname,
		Instant createdAt,
		Instant updatedAt,
		@Transient boolean isNew)
		implements Persistable<UUID> {

	// The id is client-generated (UserId.generate() in the domain) and therefore always
	// non-null, so Spring Data JDBC's default null-id "is new" check can't tell insert from
	// update on its own. JdbcUserRepository.save() determines this explicitly (an existsById
	// check) and passes it in here rather than us guessing from the id.
	@Override
	public boolean isNew() {
		return isNew;
	}

	@Override
	public UUID getId() {
		return id;
	}
}
