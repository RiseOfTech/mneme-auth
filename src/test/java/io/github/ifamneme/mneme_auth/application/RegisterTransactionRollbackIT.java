package io.github.ifamneme.mneme_auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import io.github.ifamneme.mneme_auth.PostgresIntegrationTestBase;
import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionId;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RegisterTransactionRollbackIT extends PostgresIntegrationTestBase {

	@TestConfiguration
	static class FailingSessionRepositoryConfig {

		@Bean
		@Primary
		SessionRepository failingSessionRepository() {
			return new SessionRepository() {
				@Override
				public Session save(Session generation) {
					throw new IllegalStateException("simulated session-insert failure");
				}

				@Override
				public Optional<Session> findByTokenHash(TokenHash tokenHash) {
					throw new UnsupportedOperationException();
				}

				@Override
				public boolean consumeIfLive(TokenHash tokenHash, Instant now) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void revokeLineage(SessionId sessionId, Instant now) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	@Autowired
	private RegisterUseCase registerUseCase;

	@Autowired
	private UserRepository userRepository;

	@Test
	void aFailingSessionInsertRollsBackTheUserInsertToo() {
		Email email = new Email("rollback-check@example.com");

		assertThrows(IllegalStateException.class, () -> registerUseCase.register(email.value(), "password123"));

		assertThat(userRepository.existsByEmail(email)).isFalse();
	}
}
