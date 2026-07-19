package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.CrudRepository;

interface SpringDataUserRepository extends CrudRepository<UserRecord, UUID> {

	Optional<UserRecord> findByEmail(String email);

	boolean existsByEmail(String email);
}
