package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table("users")
public record UserRecord(@Id UUID id, String email, String passwordHash, Instant createdAt, Instant updatedAt)
		implements Persistable<UUID> {

	// The id is client-generated (UserId.generate() in the domain) and therefore always
	// non-null here, so Spring Data JDBC's default null-id "is new" check would otherwise
	// treat every save() as an update matching no row instead of an insert. This slice
	// never updates an existing user, so always-new is correct for the current usage.
	@Override
	public boolean isNew() {
		return true;
	}

	@Override
	public UUID getId() {
		return id;
	}
}
