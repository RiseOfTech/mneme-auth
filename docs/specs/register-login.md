# Spec — slice/register-login

Feature slice. Delivers account creation and login that issue a working, verifiable
RS256 access token. **No refresh tokens, no sessions, no cookies** — those are the next
slice. Branch: `slice/register-login`.

## Goal

A user can register with email + password and log in with the same credentials, and each
success returns a signed **access JWT** that `mneme-core` could verify offline against the
public key. This is the first slice that produces a real token honouring the contract in
`ARCHITECTURE.md` §10. It introduces persistence (Postgres + Flyway), password hashing
(bcrypt), and RS256 signing (Nimbus) — but deliberately stops short of the refresh/session
machinery, so the token has no renewal path yet (it simply expires in 15 minutes and the
user logs in again). That is an accepted intermediate state.

## In scope

Grouped by concern. This describes intent; the plan decides the file list, following the
layout in `ARCHITECTURE.md` §2 and the naming rules in `AGENTS.md`.

**Endpoints** (both public — no authentication required to call them)
- `POST /v1/auth/register` — body `{ email, password }` → **201** `{ accessToken }`.
- `POST /v1/auth/login` — body `{ email, password }` → **200** `{ accessToken }`.

**Domain**
- A `User` aggregate: `UserId`, `Email`, `PasswordHash`, `createdAt`, `updatedAt`. Pure
  Java, no framework annotations.
- Value objects: `Email` (normalises trim + lowercase on construction, invariant:
  non-empty and contains `@`), `PasswordHash` (wraps the prefixed encoded hash string),
  `UserId` (UUID, generated in the domain at registration).
- The **raw password is not a value object** — it is a transient input to hashing and
  never becomes a stored or long-lived domain type.
- Ports (interfaces declared in the domain, in domain terms): `UserRepository`
  (`save`, `findByEmail`, `existsByEmail`), `PasswordHasher` (`hash`, `matches`),
  `TokenSigner` (build a signed access token for a user at a given time).
- Time via the injected `Clock`; `createdAt == updatedAt` on insert (per `AGENTS.md`).

**Application (use cases)**
- **Register**: reject if the (normalised) email already exists → `email_taken`; else
  generate `UserId`, hash the password, build the `User`, save it, sign an access token,
  return it.
- **Login**: resolve the user by normalised email and verify the password. **Any** failure
  — unknown email *or* wrong password — yields one indistinguishable result (§ error
  handling). On success, sign and return an access token.
- **No `@Transactional` in this slice.** Register performs a single write (`users` only);
  it relies on the repository's own transactionality. The transaction boundary enters with
  the session slice, where register becomes multi-write.

**Persistence** (Spring Data JDBC — first appearance)
- Flyway migration creating `users`: `id uuid` PK, `email text not null unique`,
  `password_hash text not null`, `created_at timestamptz not null`,
  `updated_at timestamptz not null`. Unique index on `email`. **No** `nickname`/`settings`.
- A `UserRecord` + a Spring Data JDBC repository (private to the adapter) + a
  `JdbcUserRepository` implementing the domain `UserRepository` port + a hand-written
  record↔domain mapper.
- Build dependencies added here: Spring Data JDBC starter, PostgreSQL driver, Flyway
  (with its Postgres support). Flyway is the sole schema authority — no DDL auto-generation.

**Security / crypto** (first appearance)
- `BCryptPasswordHasher` implementing `PasswordHasher` via Spring Security
  `DelegatingPasswordEncoder`, bcrypt **strength 12**; stored hashes carry the `{bcrypt}`
  prefix.
- `NimbusTokenSigner` implementing `TokenSigner`: RS256, signs with a private key **loaded
  from the environment** (PEM), sets header `kid` + `typ`, and the claims `sub` (userId),
  `iss`, `aud`, `iat`, `exp` (= `iat` + TTL). Never hand-rolled crypto.
- Configurable, not domain literals: issuer (`mneme-auth`), audience (`mneme-core`),
  access-token TTL (default **15m**), `kid`, and the key material/location — via
  `@ConfigurationProperties` / `application.yaml`.

**Web / errors**
- An `AuthApi` interface + `AuthController` (contract split per `AGENTS.md`).
- Request DTOs with edge validation (Jakarta Validation): email contains `@` and length
  ≤ 254; password `@Size` 8–64. (Normalisation stays in the `Email` VO — the edge checks
  syntax, the VO normalises; no rule duplicated.)
- `application/problem+json` (`ProblemDetail`) error handling via a
  `@RestControllerAdvice`, mirroring `mneme-core` one-for-one: `type` = `about:blank`,
  machine-readable `code` inside `properties`. Codes introduced by this slice:
  `validation_failed` (400), `email_taken` (409), `invalid_credentials` (401).

## Out of scope (do NOT do these — they are later slices or other services)

- **No refresh tokens or sessions.** No `refresh_tokens` table, no `Session` aggregate, no
  `RefreshTokenGenerator` / `TokenHasher`, no `SessionRepository`. The response body
  carries the access token and nothing else.
- **No cookies.** No `Set-Cookie` anywhere. (The refresh cookie is the next slice.)
- **No `POST /v1/auth/refresh`, no `POST /v1/auth/logout`.**
- **No incoming-token validation / resource server.** Register and login are public; do
  **not** add a `SecurityFilterChain` that protects endpoints, and do not pull
  `spring-boot-starter-security` merely to have it lock things down. Bearer validation and
  `/v1/profile` are a later slice.
- **No profile.** No `nickname`, no `settings`, no `/v1/profile`, no columns for them in
  the `users` migration.
- **No `@Transactional`** (single write — see above).
- **No JWKS endpoint** (public key is out-of-band).
- **No MapStruct** — map by hand.
- **No empty package scaffolding** beyond packages that real classes in this slice require.

## Acceptance criteria

1. `POST /v1/auth/register` with a fresh email and a valid password returns **201** with a
   non-empty `accessToken`; a single `users` row exists with the normalised email and a
   `password_hash` beginning `{bcrypt}`.
2. The issued token verifies against the RS256 **public** key, and a test asserts: header
   `alg = RS256`, `typ = JWT`, `kid` present; claims `sub` = the new user's UUID,
   `iss = mneme-auth`, `aud = mneme-core`, and `exp = iat + 15m`.
3. The raw password is never stored or logged: the only persisted form is the bcrypt hash;
   `grep` finds no plaintext-password column and no log statement emitting the raw password.
4. Registering an email that already exists **after normalisation** (e.g. `Foo@X.Y` when
   `foo@x.y` exists) returns **409** `problem+json` with `code = email_taken`, and no
   second row is created.
5. Invalid input (email without `@`, or password shorter than 8 / longer than 64) returns
   **400** `problem+json`; no row is created.
6. `POST /v1/auth/login` with correct credentials returns **200** with an access token of
   the same claim shape as (2).
7. Login with an **unknown email** and login with a **wrong password** return an
   **identical** response — same **401** status and same `problem+json` body
   (`code = invalid_credentials`) — with nothing distinguishing which half failed. A test
   asserts both branches are identical.
8. All error responses are `application/problem+json` mirroring `mneme-core` (`type`
   `about:blank`, `code` inside `properties`).
9. Domain-purity grep passes: the `domain` source set imports no `org.springframework.*`,
   `com.nimbusds.*`, `org.bouncycastle.*`, or `jakarta.persistence.*`/web types.
10. Boundary grep passes: no `Set-Cookie`, no `refresh`, no `Session`, no `/refresh` or
    `/logout`, no `@Transactional` anywhere in the diff.
11. The Flyway migration applies cleanly against a real PostgreSQL (Testcontainers) and the
    `users` table has `created_at` / `updated_at` `not null`.
12. `./gradlew build` is green, and green as seen by the human running it (§ working
    conventions).

## Tests

- **Domain unit (no Spring):** `Email` normalises case/whitespace and rejects the empty /
  no-`@` case; `User` construction stamps `createdAt == updatedAt` from a fixed `Clock`;
  `PasswordHash` preserves its prefix.
- **Use-case unit (no Spring, fake ports):** register happy path (hash → save → sign) and
  duplicate-email rejection; login success via `matches`, and the two failure branches
  (unknown email, wrong password) producing the **identical** failure.
- **Signing unit:** `NimbusTokenSigner` produces a token verifiable by the public key with
  the expected claims, under a fixed `Clock` for a deterministic `exp`, using a test
  keypair.
- **Web slice:** validation failures → 400; the controller maps use-case outcomes to
  201/200/409/401 with `problem+json`.
- **Integration (Testcontainers Postgres):** migration applies; `JdbcUserRepository`
  `save` / `findByEmail` / `existsByEmail` against a real DB; the `email` unique constraint
  holds; an end-to-end `register` → `login` over HTTP.

## Notes

- Branch `slice/register-login` off `develop`; fresh agent session; Sonnet.
- This slice first introduces persistence, Flyway, bcrypt, and RS256 signing — keep the
  refresh/session boundary hard (see Out of scope). If the plan's diff approaches ~15
  files or starts touching sessions, stop and re-split.
- **Keys.** After this slice, running the app and the signing tests requires key material:
  local dev uses a **git-ignored generated RS256 keypair** loaded via the environment
  mechanism (`ARCHITECTURE.md` §10); tests use a **fixed test keypair in test resources**
  (a test key, not a secret). `.gitignore` already covers `*.pem`.
- **Dependencies (confirm exact artifacts in the plan under Boot 4.1 / Spring Security 7,
  don't guess):** bcrypt lives in `spring-security-crypto`; do not add
  `spring-boot-starter-security` just for it. RS256 signing uses Nimbus via
  `spring-security-oauth2-jose`'s `JwtEncoder` (`ARCHITECTURE.md` §5, §10). Whatever is
  pulled must not auto-configure a filter chain that protects the public endpoints.
- `iss` / `aud` / `ttl` / `kid` are configuration values the domain is handed, not literals
  in the domain.
- The single-write / no-transaction property is intentional and stated so it is not
  "helpfully" wrapped in `@Transactional`.