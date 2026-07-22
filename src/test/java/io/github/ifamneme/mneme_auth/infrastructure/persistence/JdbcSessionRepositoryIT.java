package io.github.ifamneme.mneme_auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.ifamneme.mneme_auth.PostgresIntegrationTestBase;
import io.github.ifamneme.mneme_auth.domain.Session;
import io.github.ifamneme.mneme_auth.domain.SessionRepository;
import io.github.ifamneme.mneme_auth.domain.TokenHash;
import io.github.ifamneme.mneme_auth.domain.UserId;
import io.github.ifamneme.mneme_auth.domain.UserRepository;
import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.PasswordHash;
import io.github.ifamneme.mneme_auth.domain.User;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcSessionRepositoryIT extends PostgresIntegrationTestBase {

	private static final Duration IDLE_WINDOW = Duration.ofDays(30);

	@Autowired
	private SessionRepository sessionRepository;

	@Autowired
	private UserRepository userRepository;

	private UserId persistUser(String email) {
		User user = User.register(
				UserId.generate(), new Email(email), new PasswordHash("{bcrypt}$2a$12$hash"),
				Instant.parse("2026-07-19T10:00:00Z"));
		return userRepository.save(user).id();
	}

	@Test
	void migrationAppliesAndSessionRoundTrips() {
		UserId userId = persistUser("session-roundtrip@example.com");
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		Session opened = Session.open(userId, new TokenHash(fakeHash("roundtrip")), now, IDLE_WINDOW);

		sessionRepository.save(opened);

		Session found = sessionRepository.findByTokenHash(new TokenHash(fakeHash("roundtrip"))).orElseThrow();
		assertThat(found.userId()).isEqualTo(userId);
		assertThat(found.id()).isEqualTo(opened.id());
		assertThat(found.consumedAt()).isNull();
		assertThat(found.expiresAt()).isEqualTo(now.plus(IDLE_WINDOW));
	}

	@Test
	void enforcesUniqueTokenHashConstraint() {
		UserId userId = persistUser("dup-hash@example.com");
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		TokenHash sharedHash = new TokenHash(fakeHash("shared"));
		sessionRepository.save(Session.open(userId, sharedHash, now, IDLE_WINDOW));

		assertThrows(
				DataIntegrityViolationException.class,
				() -> sessionRepository.save(Session.open(userId, sharedHash, now, IDLE_WINDOW)));
	}

	@Test
	void enforcesUserForeignKeyConstraint() {
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		Session orphan = Session.open(UserId.generate(), new TokenHash(fakeHash("orphan")), now, IDLE_WINDOW);

		assertThrows(DataIntegrityViolationException.class, () -> sessionRepository.save(orphan));
	}

	@Test
	void exactlyOneOfTwoConcurrentConsumesWins() throws Exception {
		UserId userId = persistUser("concurrency@example.com");
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		TokenHash hash = new TokenHash(fakeHash("concurrent"));
		sessionRepository.save(Session.open(userId, hash, now, IDLE_WINDOW));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch startLatch = new CountDownLatch(1);
		Callable<Boolean> consumeTask = () -> {
			startLatch.await();
			return sessionRepository.consumeIfLive(hash, now);
		};
		try {
			Future<Boolean> first = executor.submit(consumeTask);
			Future<Boolean> second = executor.submit(consumeTask);
			startLatch.countDown();

			Set<Boolean> results = Set.of(first.get(), second.get());
			assertThat(results).isEqualTo(Set.of(true, false));
		} finally {
			executor.shutdown();
		}

		Session consumed = sessionRepository.findByTokenHash(hash).orElseThrow();
		assertThat(consumed.consumedAt()).isNotNull();
	}

	@Test
	void revokeLineageMarksEveryLiveRowForThatSessionIdAsConsumed() {
		UserId userId = persistUser("revoke-lineage@example.com");
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		Session first = Session.open(userId, new TokenHash(fakeHash("revoke-1")), now, IDLE_WINDOW);
		sessionRepository.save(first);
		Session second = first.rotate(new TokenHash(fakeHash("revoke-2")), now.plusSeconds(60), IDLE_WINDOW);
		sessionRepository.save(second);

		sessionRepository.revokeLineage(first.id(), now.plusSeconds(120));

		Session liveGeneration =
				sessionRepository.findByTokenHash(new TokenHash(fakeHash("revoke-2"))).orElseThrow();
		assertThat(liveGeneration.consumedAt()).isEqualTo(now.plusSeconds(120));
	}

	@Test
	void revokeLineageDoesNotAffectAnotherSessionsLineage() {
		UserId userId = persistUser("lineage-isolation@example.com");
		Instant now = Instant.parse("2026-07-19T10:00:00Z");
		Session sessionA = Session.open(userId, new TokenHash(fakeHash("lineage-a")), now, IDLE_WINDOW);
		Session sessionB = Session.open(userId, new TokenHash(fakeHash("lineage-b")), now, IDLE_WINDOW);
		sessionRepository.save(sessionA);
		sessionRepository.save(sessionB);

		sessionRepository.revokeLineage(sessionA.id(), now.plusSeconds(60));

		Session foundB = sessionRepository.findByTokenHash(new TokenHash(fakeHash("lineage-b"))).orElseThrow();
		assertThat(foundB.consumedAt()).isNull();
	}

	private static String fakeHash(String seed) {
		// These persistence-level tests only need a distinct, valid 64-hex-char value per
		// seed for the token_hash column; reusing SHA-256 here is just a convenient way to
		// get that shape, not a test of Sha256TokenHasher itself.
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
