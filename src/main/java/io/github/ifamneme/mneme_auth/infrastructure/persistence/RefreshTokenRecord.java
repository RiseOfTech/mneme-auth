package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

@Table("refresh_tokens")
public record RefreshTokenRecord(
		@Id UUID id,
		UUID userId,
		UUID sessionId,
		String tokenHash,
		Instant consumedAt,
		Instant expiresAt,
		Instant createdAt,
		Instant updatedAt)
		implements Persistable<UUID> {

	// Every RefreshTokenRecord this adapter saves is a brand-new generation row (the id is
	// generated fresh per insert by the mapper, never reused); mutations to an existing row
	// (consumed_at) happen exclusively through the @Modifying queries below, never through
	// CrudRepository.save(). So "always new" is correct here, same reasoning as UserRecord.
	@Override
	public boolean isNew() {
		return true;
	}

	@Override
	public UUID getId() {
		return id;
	}
}
