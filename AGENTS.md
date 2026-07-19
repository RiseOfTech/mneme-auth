# mneme-auth — Project guidelines

This file extends the global engineering standards (`~/.config/opencode/AGENTS.md`).
Project-specific rules below take precedence when they conflict with global rules.

`mneme-auth` is the identity service of the **Mneme** product: it owns user registration,
login, session lifecycle (refresh token rotation and logout), and the user's own profile.
Product scope lives in `PROJECT.md`; the authoritative design lives in `ARCHITECTURE.md`.
Read `ARCHITECTURE.md` before implementing — if it is absent, the architecture has not
been decided yet, so do not invent structure: ask.

`mneme-auth` issues the access tokens that `mneme-core` consumes. The **token contract**
(`ARCHITECTURE.md` §10) is normative: do not change its algorithm, claims, or key handling
without a design decision, because another service verifies against it.

## Stack

- Language: Java 21
- Framework: Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Jakarta EE 11,
  Jackson 3)
- Build: Gradle (Kotlin DSL)
- Persistence: PostgreSQL via **Spring Data JDBC** + Flyway (decided — see rules)
- JWT/JOSE: Nimbus, via `spring-security-oauth2-jose`
- Password hashing: Spring Security `DelegatingPasswordEncoder` (bcrypt)
- Group: `io.github.ifamneme`
- Root package: `io.github.ifamneme.mneme_auth` (sub-package layout per `ARCHITECTURE.md`)

## Build and run

- Build: `./gradlew build`
- Test: `./gradlew test`
- Run: `./gradlew bootRun`
- Health (k8s probes): `GET /actuator/health`

Running requires the RS256 signing key material in the environment (a git-ignored dev
keypair locally, loaded through the same mechanism as production — see `ARCHITECTURE.md`
§10). Run `./gradlew build` after any significant change to confirm it compiles and tests
pass.

## Project-specific rules

- REST endpoints are versioned under `/v1`.
- **Persistence is Spring Data JDBC — decided.** Add the Spring Data JDBC starter, the
  Postgres driver, and Flyway; model tables via records (`UserRecord`,
  `RefreshTokenRecord`) inside the persistence adapter only. Do **NOT** add JPA/Hibernate
  — it was rejected (`ARCHITECTURE.md` §12). Spring Data JDBC generates no DDL, so Flyway
  is the sole schema authority; every schema change is a checked-in migration.
- Do NOT invent the package/module layout; it is defined in `ARCHITECTURE.md`. Until then
  keep changes to the existing skeleton minimal.
- This repo knows only its own models. It shares no model libraries with other services
  (`mneme-core`, `mneme-web`). Cross-service contracts are expressed as OpenAPI/spec;
  external models are mapped to ours at the edge (anti-corruption layer).
- Interfaces at behavioural boundaries. Every use case is an interface in `application`
  with a single method, implemented by exactly one class. Controllers and other adapters
  depend on the interface, never the implementation. Ports (`UserRepository`,
  `SessionRepository`, `PasswordHasher`, `TokenSigner`, `RefreshTokenGenerator`,
  `TokenHasher`) are interfaces declared by the inner layer. Mappers are interfaces too.
- No interfaces over data. Aggregates, value objects, persistence records, DTOs, and
  `@ConfigurationProperties` types stay concrete. An interface with no behaviour and only
  one conceivable implementation adds noise, not abstraction.
- Implementation naming: when the implementation is characterised by its mechanism, the
  prefix names it (`JdbcUserRepository`, `NimbusTokenSigner`, `BCryptPasswordHasher`,
  `Sha256TokenHasher`). When there is nothing to say beyond "the one implementation",
  prefix with `Default` (`DefaultRegisterUseCase`, `DefaultUserMapper`).
- The domain never calls `Instant.now()`. Time enters as a parameter or via an injected
  `java.time.Clock`. This is load-bearing here: token `exp`/`iat`, session `expiresAt`,
  and `consumedAt` are all time, and the rotation/expiry logic must be testable under a
  fixed clock.

### Security rules (always apply — never relax without a design decision)

These encode `ARCHITECTURE.md` §8–§10 and §12. They hold in every slice.

- **Never hand-roll crypto.** JWT signing goes through Nimbus (`TokenSigner` impl);
  password hashing through Spring Security's encoder (`PasswordHasher` impl). No custom
  signature, hashing, or random-token scheme beyond the ports defined in the design.
- **Passwords never in plaintext, anywhere** — not in the database, not in logs, not in
  exceptions. Hash with bcrypt (strength 12) via `DelegatingPasswordEncoder`. Do NOT swap
  to argon2 or any other algorithm without a design decision.
- **The signing private key is a secret from the environment. Never commit it** — no key
  material, dev or prod, in git.
- **The refresh token is an opaque random string, NOT a JWT.** Store only its SHA-256
  hash (`TokenHash`); never store or log the raw token. The raw value leaves the service
  only in the `Set-Cookie` header.
- **Refresh token travels only in an `HttpOnly; Secure; SameSite=Lax` cookie scoped to
  `Path=/v1/auth/refresh`** — never in a response or request body. Access token goes in
  the response body; it is not put in a cookie or in any server-side session.
- **Access-token claims stay minimal** — `sub`, `iss`, `aud`, `iat`, `exp`, plus `kid` in
  the header. Do NOT add `jti`, roles, scopes, or profile data.
- **No JWKS endpoint.** The public verification key is distributed out-of-band; do not
  build a key endpoint (§10, §12).
- **`/login` must not reveal which half of the credential pair was wrong.** "Email not
  found" and "wrong password" return one identical `401` (`invalid_credentials`). This is
  a hard anti-enumeration rule, not a UX detail. (`/register` returning `409 email_taken`
  is a separate, consciously accepted compromise — see `ARCHITECTURE.md` §12–§13.)
- **`/logout` always returns `204`** and clears the cookie, whether or not the refresh
  token was valid. It does not reveal token validity.
- **On reuse detection** (a consumed refresh token replayed), revoke the entire session
  lineage, not just the offending row (`ARCHITECTURE.md` §4).

### Domain purity (grep-checkable)

The `domain` source set imports no framework types: no `org.springframework.*`, no
`com.nimbusds.*`, no `org.bouncycastle.*`, no `jakarta.persistence.*`, no web types. JDK
`java.security.*` (used behind the crypto ports' *interfaces* only as domain-termed value
types, not implementations) does not count as a framework. If a framework import appears
in `domain`, the boundary is drawn wrong — fix the boundary, don't suppress the check.

## Testing

- JUnit 5 (Spring Boot default). Per the global standards: unit-test the domain and use
  cases **without a Spring context**, substituting fakes for the ports — including
  `Clock`, `RefreshTokenGenerator`, and `TokenHasher`, whose whole purpose is to make the
  rotation / reuse-detection / idle-expiry logic deterministic in tests.
- Integration-test adapters against real infrastructure (Testcontainers Postgres) —
  migrations apply cleanly, unique constraints hold, the token-hash lookup and lineage
  revocation behave against a real database.

## Out of scope (MVP)

See `PROJECT.md`. In short: no email verification or password recovery, no OAuth/social
login, no roles or permissions, no MFA, no JWKS endpoint, no avatars, and no changes to
`mneme-core` (its `Authenticator` reimplementation is a `mneme-core` slice — this repo
defines only the token contract). Do not build these.