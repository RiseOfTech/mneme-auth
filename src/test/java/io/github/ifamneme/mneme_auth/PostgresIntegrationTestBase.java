package io.github.ifamneme.mneme_auth;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresIntegrationTestBase {

	// Singleton-container pattern: this static field is shared by every subclass, so it must
	// be started exactly once and never explicitly stopped - Testcontainers' Ryuk reaper
	// cleans it up at JVM exit. @Testcontainers/@Container instead stop the container after
	// each test CLASS's afterAll, which - since the field is one shared instance across all
	// subclasses - breaks every subclass that runs after the first (stale/broken connection
	// pools in cached Spring contexts, or a restarted container losing its data mid-suite).
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
