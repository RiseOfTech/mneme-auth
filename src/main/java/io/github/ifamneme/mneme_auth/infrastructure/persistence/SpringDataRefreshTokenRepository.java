package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface SpringDataRefreshTokenRepository extends CrudRepository<RefreshTokenRecord, UUID> {

	Optional<RefreshTokenRecord> findByTokenHash(String tokenHash);

	@Modifying
	@Query(
			"""
			UPDATE refresh_tokens
			SET consumed_at = :now, updated_at = :now
			WHERE token_hash = :tokenHash AND consumed_at IS NULL AND expires_at > :now
			""")
	int consumeIfLive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	@Modifying
	@Query(
			"""
			UPDATE refresh_tokens
			SET consumed_at = :now, updated_at = :now
			WHERE session_id = :sessionId AND consumed_at IS NULL
			""")
	int revokeLineage(@Param("sessionId") UUID sessionId, @Param("now") Instant now);
}
