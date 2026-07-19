# mneme-auth — Architecture

Authoritative design for the `mneme-auth` service. This is the "how".

- **Scope and product decisions** live in `PROJECT.md`. Not repeated here.
- **Engineering standards** (Clean Architecture, SOLID, Clean Code, testing) live in
  the global `AGENTS.md`. Not repeated here — this document assumes them and only
  records project-specific structure and decisions.
- **Stack and conventions** live in the project `AGENTS.md`.

When this document conflicts with a lower-level guess, this document wins. When a
decision is genuinely not made yet, it is listed under "Open / deferred" — do not
invent it, ask.

This service is the first one that changes how `mneme-core` authenticates. The
**token contract** in §10 is the normative interface `mneme-core` will consume when its
`Authenticator` port is reimplemented (a separate `mneme-core` slice). Treat §10 as the
single source of truth for that contract.

---

## 1. Style and layering

Clean Architecture, layer-first. The dependency rule is absolute: outer layers depend
on inner layers, never the reverse. Frameworks (Spring, Spring Security, Nimbus JOSE,
JDBC, HTTP) are details at the edges. The domain is the most stable layer and compiles
without Spring, Spring Security, Nimbus, BouncyCastle, or any web or persistence type on
its classpath.

**Three model kinds — never collapse them into fewer:**

1. **Domain model** — `User`, `Session`, value objects (`Email`, `PasswordHash`,
   `TokenHash`, `UserId`, `SessionId`). Pure Java, zero framework annotations. Holds
   behaviour and invariants.
2. **Persistence model** — Spring Data JDBC records (`UserRecord`, `RefreshTokenRecord`).
   Live **only** inside the persistence adapter. Never returned to or accepted from upper
   layers.
3. **DTOs** — request/response types at the REST edge. Live only in the web adapter.

Two mappings, both at the edges: domain↔persistence (in the persistence adapter) and
domain↔dto (in the web adapter). The domain in the middle knows about neither. Mapping is
written by hand to start (the models are not 1:1 — e.g. `Email` is a value object in the
domain, a `text` column in the table). Introduce MapStruct only if the number of mappers
grows enough to justify it.

**The layer boundary is machine-checkable and must stay so.** The domain source set
imports no `org.springframework.*`, no `com.nimbusds.*`, no `org.bouncycastle.*`, no
`jakarta.*` web/persistence type. This is a grep-able invariant, not a matter of
inspection (see `AGENTS.md`).

---

## 2. Module and package layout

**Single Gradle module.** Multi-module (`:domain`, `:application`, `:infrastructure`) is
a recorded upgrade path — it turns the dependency rule into a compiler-enforced wall —
but is premature for a single-developer MVP. Adopt it only if the boundary starts leaking
or a deploy goal specifically calls for it.

Layer-first packages under the root package `io.github.ifamneme.mneme_auth`:

```
io.github.ifamneme.mneme_auth
├── domain              // User, Session, value objects, domain services,
│                       // repository PORT interfaces, crypto PORT interfaces
├── application         // use cases; view models; transaction boundary
└── infrastructure
    ├── persistence     // Spring Data JDBC records + interfaces,
    │                   // repository port implementations, mappers
    ├── web             // controllers, *Api interfaces, request/response DTOs,
    │                   // Spring Security config, resource-server (token validation)
    ├── security        // NimbusTokenSigner, BCryptPasswordHasher,
    │                   // SecureRandomRefreshTokenGenerator, Sha256TokenHasher, key loading
    └── config          // Spring configuration, @ConfigurationProperties
```

**Ports live with the domain, not with the adapters.** `UserRepository` (interface) is in
`domain`; `JdbcUserRepository` (implementation) is in `infrastructure/persistence`.
`TokenSigner` (interface) is in `domain`; `NimbusTokenSigner` is in
`infrastructure/security`. Infrastructure depends on the domain by implementing its
interfaces — Dependency Inversion made visible in the package topology. If an interface
for an outbound dependency ends up in `infrastructure`, the boundary has been drawn the
wrong way round.

---

## 3. Domain model

### Aggregates

Two aggregates, deliberately split:

- **`User`** — the account and identity: `id` (`UserId`), `email` (`Email`),
  `passwordHash` (`PasswordHash`). The profile (nickname, settings) is added to this
  aggregate in a **later slice**, not the first.
- **`Session`** — one authenticated device's lifecycle: the refresh-token lineage. A
  session owns a chain of refresh-token generations (see §4, §6). Identified by
  `SessionId`.

They are linked **only by `UserId` value** — no object references between the two roots.
The relationship is 1:N (a user has many concurrent sessions — one per device).

**Why two aggregates, not one.** Account identity and session lifecycle change
independently and for different reasons (registration/credential change vs
login/refresh/logout on a device). There is no invariant that must hold atomically
between a user's credentials and the set of live sessions. Keeping them separate makes an
important guarantee structural rather than disciplinary:

> **Refresh/rotation never touches the user's credentials, and credential changes never
> load sessions.**

What this split is **not**: not two services, not eventual consistency, not domain events
between them. Everything is synchronous, in-process, in one transaction where needed (§4).
The separation is at the aggregate/repository level only.

### Session and the refresh-token lineage

Terminology, fixed to avoid ambiguity:

- **token family / session lineage** — the chain of refresh-token generations belonging
  to one `Session`. "Family" here means *generations of one session's token*, **not** a
  group of people. (The product's user base is small — that is a scale note, unrelated to
  this term.)
- **generation** — one refresh token in the chain. Each `/refresh` consumes the current
  generation and issues the next, same `SessionId`.

A `Session` is created at login/registration and ends when it is revoked (logout, idle
expiry, or reuse detection — §4). Sessions are per-device and independently revocable.

### Identity and ownership

- `UserId` is a UUID **generated in the domain** at registration (the user has identity in
  memory before being persisted). It is the value placed in the access token's `sub` and
  is the same id `mneme-core` reads as `MeResponseDto.userId`. The database only stores it.
- `SessionId` is likewise a domain-generated UUID, stable across the whole lineage.
- `UserId` arrives in the application layer as an **explicit use-case parameter** for
  authenticated operations. It is never read from a `SecurityContext` inside the domain or
  use case. Extraction from the validated access token happens at the edge (§8).

### Value objects

`Email`, `PasswordHash`, `TokenHash`, `RawRefreshToken`, `UserId`, `SessionId` are value
objects, not bare strings — they make illegal states harder to represent and keep
validation and normalisation in one place.

- **`Email`** normalises on construction: trim + lowercase. Two inputs differing only in
  case or surrounding whitespace produce equal `Email` values. Syntactic validation is
  deliberately loose (contains `@`, bounded length ≤ 254) — see §12; the MVP does not
  verify deliverability, so strict RFC parsing buys nothing.
- **`PasswordHash`** wraps the encoded hash string including its algorithm prefix
  (`{bcrypt}…`). The domain never holds a raw password beyond the transient input to
  hashing.
- **`RawRefreshToken`** is the high-entropy secret handed to the client once, never
  stored. **`TokenHash`** is its SHA-256, which is what the database holds (§6).

---

## 4. Application layer (use cases)

Use cases orchestrate the domain; they do not contain domain logic.

MVP use cases (indicative), first slice marked:

- **Register** *(slice 1)* — validate email + password, generate `UserId`, hash the
  password, persist the `User`, open the first `Session`, issue an access token + first
  refresh generation. Multi-write (user + session) → transactional (see boundary below).
- **Login** *(slice 1)* — resolve the user by email, verify the password, open a new
  `Session`, issue tokens. On failure, a single indistinguishable outcome (§7).
- **Refresh** *(later slice)* — rotate the current refresh generation, return a new
  access token; run reuse detection (below).
- **Logout** *(later slice)* — revoke the current session.
- **Get / update profile** *(later slice)* — nickname and settings.

### Refresh, rotation, and reuse detection (the L2 rule)

This is the security core of the service. A refresh request carries the raw refresh token
(from the cookie, §8). The use case:

1. Hash the incoming token; look up the generation by `TokenHash`. **Not found → reject
   (401).**
2. **Already consumed** (`consumedAt` is set) → this is a replay of a spent token: the
   lineage is compromised. **Revoke the entire session (whole lineage) and reject (401).**
   This is reuse detection — it fires whether the replayer is the thief or the legitimate
   client arriving with a token the thief already rotated.
3. **Expired** (`expiresAt` in the past) → the session lapsed by idle timeout. Revoke the
   lineage and reject (401).
4. Otherwise **rotate**: mark the current generation consumed (`consumedAt = now`), insert
   a new generation in the same lineage with a fresh token and `expiresAt = now + idle
   window`, return the new access token (and set the new refresh cookie).

Idle window is **sliding**: every rotation moves `expiresAt` forward from now. An actively
used session never expires; an untouched one lapses after the idle window (§ token
lifetimes below).

### Token lifetimes

- **Access token: 15 minutes.** This is the un-revocable window — `mneme-core` validates
  by signature and cannot revoke a single access token, so it is kept short.
- **Refresh (session) idle window: 30 days, sliding.** Revocation happens via logout or
  reuse detection, not via a fixed absolute cap (a "logged in while you use it" model
  suited to a daily-study app). The absolute-cap alternative was considered and rejected
  (§ Rejected).

### Transaction boundary

Pragmatic choice, following `mneme-core`: `@Transactional` on the application service (use
case), and **only** where a use case performs more than one write. In MVP that is
**register** (user + first session) and any refresh rotation that both consumes and
inserts within one unit. Single-write use cases rely on the repository's own
transactionality and get no annotation. Never `@Transactional` on the domain or on
controllers.

This is a conscious compromise: the application layer imports one Spring annotation and is
therefore not 100% framework-free. **Escape hatch:** if multi-write use cases proliferate,
or use cases must run in tests with no Spring on the classpath, introduce a
`TransactionRunner` port (interface in `application`, Spring impl in the adapter). Not
before.

---

## 5. Ports

Outbound dependencies are interfaces declared by the inner layers; adapters implement
them. All are declared in domain terms — no SQL, no Nimbus type, no Spring type in any
signature.

- **`UserRepository`** — `save(User)`, `findByEmail(Email)`, `existsByEmail(Email)`,
  `findById(UserId)`. Declared in `domain`.
- **`SessionRepository`** — `save(Session)` / persist a generation,
  `findByTokenHash(TokenHash)`, `revokeLineage(SessionId)`, `deleteExpired(Instant)`.
  Declared in `domain`. (Exact method shape is an implementation detail of the refresh
  slice; the port stays domain-termed.)
- **`PasswordHasher`** — `hash(raw) → PasswordHash`, `matches(raw, PasswordHash) →
  boolean`. Declared in `domain`; implemented by `BCryptPasswordHasher` over Spring
  Security's `DelegatingPasswordEncoder` (§ crypto).
- **`TokenSigner`** — `sign(AccessTokenClaims) → signed JWT string`. Declared in `domain`;
  implemented by `NimbusTokenSigner` (§ crypto, §10).
- **`RefreshTokenGenerator`** — `generate() → RawRefreshToken`. Declared in `domain`;
  implemented by a `SecureRandom`-backed adapter. Behind a port so the randomness source
  is substitutable in tests (see rationale below).
- **`TokenHasher`** — `hash(RawRefreshToken) → TokenHash` (SHA-256). Declared in `domain`;
  deterministic, but behind a port for the same test-substitution reason.
- **`Clock`** — time enters the domain via an injected `java.time.Clock`, never
  `Instant.now()` (carried over from global standards; critical here because token
  `exp`/`iat`, `expiresAt`, and `consumedAt` are all time).

**Why `RefreshTokenGenerator`/`TokenHasher` are ports over one-line operations.** Not for
abstraction's sake — there is exactly one implementation of each. The reason is
**determinism in tests**: the L2 rules (rotation, reuse detection, idle expiry) are the
part most worth testing, and they cannot be exercised cleanly if the randomness source and
hash are hard-wired into the domain. This is the same justification that puts time behind
`Clock`. If that rationale ever stops holding, these collapse back into the domain.

The only outbound ports to external systems are persistence (Postgres) and the signing key
material (loaded at the edge, §crypto). There is no port that calls another mneme service —
`mneme-auth` is called *by* clients, it does not call `mneme-core`.

### Crypto adapters (infrastructure/security)

The domain crypto ports above are implemented here, where the framework/library types
live:

- **`BCryptPasswordHasher`** — wraps Spring Security `DelegatingPasswordEncoder` with
  `bcrypt` as the encoding algorithm, strength 12. The stored hash carries its `{bcrypt}`
  prefix, so migrating to another algorithm later is free (the encoder selects by prefix).
  Do not hand-roll hashing.
- **`NimbusTokenSigner`** — signs the access token with the RS256 private key via Nimbus
  JOSE (through `spring-security-oauth2-jose`'s `JwtEncoder`/Nimbus). Never invent crypto.
- **`SecureRandom`-backed `RefreshTokenGenerator`** — 256 bits from a CSPRNG, encoded
  URL-safe.
- **`Sha256TokenHasher`** — `java.security.MessageDigest` SHA-256, hex-encoded. No salt:
  the input is 256-bit high-entropy, so salting buys nothing and a deterministic hash
  allows an indexed lookup (§6, § Rejected).

---

## 6. Persistence adapter

Stack decision: **Spring Data JDBC + Flyway + PostgreSQL driver.** JPA/Hibernate is
**not** used (§ Rejected). Spring Data JDBC maps records to tables without an identity
map, lazy loading, or dirty checking — none of which this schema needs — and does not
tempt the persistence model to double as the domain model.

The rule that keeps persistence from eroding the architecture:

> All persistence types (records, Spring Data repositories, mappers) live inside the
> persistence adapter and leave it via no class, interface, or annotation.

Concrete rules:

- **Separate records.** `UserRecord` / `RefreshTokenRecord` carry the Spring Data
  `@Table`/`@Id` mapping; the domain `User` / `Session` stay annotation-free. Do not
  annotate the domain types.
- **Repository port vs Spring Data.** The domain port `UserRepository` is implemented by
  `JdbcUserRepository` in the adapter, which internally uses a Spring Data
  `CrudRepository`/`ListCrudRepository` over `UserRecord` and maps record↔domain. The
  Spring Data interface is a private detail of the adapter and does not escape the
  `persistence` package. A use case never depends on a Spring Data type.
- **No cross-aggregate reference.** No association between `UserRecord` and
  `RefreshTokenRecord`. The link is the `user_id` value only.

### Tables

```
users
  id             uuid        primary key
  email          text        not null, unique       -- normalised (trim + lowercase)
  password_hash  text        not null               -- '{bcrypt}$2b$12$…'
  created_at     timestamptz not null
  updated_at     timestamptz not null
  -- nickname / settings: added by the profile slice, its own migration

refresh_tokens
  id           uuid        primary key
  user_id      uuid        not null                 -- owner (value link, no FK object nav)
  session_id   uuid        not null                 -- the lineage / family
  token_hash   char(64)    not null, unique         -- SHA-256(raw) hex; the lookup key
  consumed_at  timestamptz null                     -- set on rotation (L2 reuse detection)
  expires_at   timestamptz not null                 -- sliding idle expiry
  created_at   timestamptz not null
```

- **`email` unique index** enforces one account per normalised email — the uniqueness the
  domain assumes is guaranteed by the schema, not by hope.
- **`token_hash` unique index** is the hot lookup path for `/refresh`. The stored value is
  a SHA-256 (fast, high-entropy input — not a password), so an indexed equality lookup is
  correct and cheap.
- **`session_id`** groups a lineage: revocation on logout or reuse detection is
  `DELETE … WHERE session_id = ?` (or a soft-revoke equivalent). Index `(session_id)` for
  that path.
- **A real FK `refresh_tokens.user_id → users.id`** exists at the database level for
  integrity. It does **not** imply object navigation between records.
- **Enums** (none in the MVP tables) would be stored as strings, never ordinal — carried
  over from `mneme-core`.

### One table, not two (sessions + refresh_tokens)

Sessions are represented by the `session_id` column on `refresh_tokens`, **not** a
separate `sessions` table. Everything the MVP needs — multi-device (N lineages per user),
reuse detection, logout by lineage, sliding expiry — works on this single table. A
dedicated `sessions` entity earns its place only when per-device metadata and a
"your devices" screen appear (user-agent, last-used, a human label). That is not in the
MVP. **Recorded door:** if that screen is built, extract `sessions` from `session_id` in a
migration — non-breaking, because the lineage id already exists.

### Cleanup

Consumed and expired generations accumulate; a periodic sweep
(`DELETE … WHERE expires_at < now()` and consumed rows past retention) reclaims them. This
belongs to a maintenance/logout slice, **not** slice 1. Slice 1 may leave rows in place.

### Schema management

Schema is owned by **Flyway migrations** (checked into git), one migration per slice.
There is no `ddl-auto` object-to-schema generation to rely on — Spring Data JDBC does not
generate DDL, so Flyway is the sole schema authority from the first slice. Slice 1's
migration creates `users` and `refresh_tokens`.

---

## 7. Web adapter (REST)

Versioned under `/v1`. `userId` never appears in paths — the caller's identity comes from
the validated access token (§8), not the URL. Endpoints:

**`/v1/auth`** — the credential and session lifecycle (public except where noted).

- `POST /v1/auth/register` — body `{ email, password }`. **201**, body `{ accessToken }`,
  plus `Set-Cookie` with the refresh token. *(slice 1)*
- `POST /v1/auth/login` — body `{ email, password }`. **200**, body `{ accessToken }`,
  plus `Set-Cookie`. *(slice 1)*
- `POST /v1/auth/refresh` — no body; refresh token read from the cookie. **200**, body
  `{ accessToken }`, plus a rotated `Set-Cookie`. Invalid/absent/expired/replayed →
  **401**. *(later slice)*
- `POST /v1/auth/logout` — no body; refresh token from the cookie. **204 always**
  (idempotent: expresses "be logged out"; does not reveal whether the token was valid),
  plus a cookie-clearing `Set-Cookie: …; Max-Age=0`. *(later slice)*

**`/v1/profile`** — the caller's profile (authenticated). *(later slice)*

- `GET /v1/profile` — **200** `{ userId, nickname, … }`.
- `PUT /v1/profile` — body `{ nickname, … }`. **200**.

**No JWKS / public-key endpoint.** The public verification key is distributed to
`mneme-core` out-of-band (§10). This is deliberate; see § Rejected for the JWKS door.

**Controller contract split.** Each resource is a dedicated `*Api` interface (e.g.
`AuthApi`, `ProfileApi`) carrying the method-level mapping annotations; the `*Controller`
class implements it. Separates the REST contract from handler logic and gives a single
place to diff against an OpenAPI spec later. Structural convention, not a runtime concern
— carried over from `mneme-core`.

The service exposes domain-shaped resources, never endpoints shaped to a particular
front-end screen. Screen-specific aggregation is a BFF concern and lives in `mneme-web`,
not here.

---

## 8. Authentication (this service's own)

`mneme-auth` is both the **issuer** of access tokens and a **resource server** for its own
protected routes (`/v1/profile`). Two distinct responsibilities:

**Issuing** (register / login / refresh) — the use case builds `AccessTokenClaims` (§10)
and calls the `TokenSigner` port; the signed JWT goes in the response body. The refresh
token is generated (`RefreshTokenGenerator`), its hash persisted, and the raw value set as
a cookie (below). Password verification uses the `PasswordHasher` port.

**Validating its own incoming tokens** (`/v1/profile`) — handled by **Spring Security
resource-server** (Nimbus decoder) configured with the **same public key** used to verify
signatures. The filter validates signature, `exp`, `iss`, and `aud`, then the caller's
`UserId` is extracted from `sub` and passed into the use case **as a parameter**. The
domain and application never read a `SecurityContext`. This mirrors `mneme-core`'s
`Authenticator` seam — the extraction is an edge detail; the use case sees only `UserId`.

> When `mneme-core` reimplements its `Authenticator` to validate JWTs, it performs exactly
> this validation with the same public key. §10 is the shared contract. The resource-server
> configuration here is a working reference for that `mneme-core` slice.

### The refresh-token cookie

The refresh token is delivered and accepted **only** as an HTTP cookie, never in a
response or request body:

```
Set-Cookie: <name>=<raw refresh token>;
            HttpOnly; Secure; SameSite=Lax; Path=/v1/auth/refresh
```

- **`HttpOnly`** — JS cannot read it; an XSS injection cannot exfiltrate the long-lived
  secret. This is the whole point of splitting it from the access token, which *is*
  JS-readable (it must go in the `Authorization` header to `mneme-core`) but lives only
  **in memory** on the client and only 15 minutes.
- **`Secure`** — HTTPS only. (`localhost` is treated as a secure context in dev.)
- **`SameSite=Lax`** — a third-party site cannot trigger a credentialed request, which
  closes the basic CSRF vector **without a CSRF token**. This holds **because
  `mneme-web` and `mneme-auth` are sibling subdomains of one site** (same-site). That
  topology is therefore an architectural requirement, not a deployment accident.
- **`Path=/v1/auth/refresh`** — the cookie is only attached where it is needed (refresh
  and logout share the path prefix). It is never sent to `/v1/profile` or anywhere else.

`mneme-core` is never in this picture: it only ever sees `Authorization: Bearer
<access>`. The refresh cookie lives strictly between `mneme-web` and `mneme-auth`.

---

## 9. Passwords

- **bcrypt, strength 12**, via Spring Security `DelegatingPasswordEncoder`, behind the
  `PasswordHasher` port. Never stored or logged in plaintext, anywhere.
- **Input bounds:** password length 8–64. No composition rules (no "one uppercase + one
  symbol") — length over composition, per current NIST guidance; composition rules lower
  real-world entropy and annoy users. The 64 upper bound also avoids bcrypt's 72-byte
  silent truncation.
- **argon2** is a recorded upgrade path (OWASP's nominal first choice) but **not** used in
  MVP; see § Rejected for the reasoning (Spring's argon2 impl requires BouncyCastle and
  carries an attacker/defender asymmetry caveat). The `{bcrypt}` prefix makes the switch
  migration-free.

---

## 10. Token contract (the interface `mneme-core` consumes)

This section is normative. `mneme-core`'s future JWT `Authenticator` validates exactly
this.

- **Type:** JWS, compact JWT. **Algorithm: RS256** (asymmetric). `mneme-auth` signs with
  the private key; verifiers use the public key.
- **Header:** `alg: RS256`, `typ: JWT`, **`kid`** (key id) — present from day one so a
  future multi-key/rotation story (or a JWKS endpoint) is non-breaking, even though only
  one key exists now.
- **Claims (minimal — do not add):**
    - `sub` — the `userId` (UUID string). The same id `mneme-core` returns as
      `MeResponseDto.userId`.
    - `iss` — `"mneme-auth"`. Verifiers check this literal.
    - `aud` — `"mneme-core"`. Verifiers check this literal. Present so a token minted for one
      consumer cannot be replayed at a different one if the system grows.
    - `iat`, `exp` — issued-at and expiry; `exp = iat + 15min`.
    - **No `jti`, no roles/scopes, no profile data.** One kind of user; access tokens are
      not individually revocable by design (revocation is a refresh-layer concern).

### Key management

- **Algorithm keypair: RS256.** The **private key is a secret from the environment**
  (PEM), never committed to git. `mneme-auth` loads it at startup to sign.
- **The public key is distributed to `mneme-core` out-of-band** (configuration / env), and
  `mneme-core` verifies offline with it (`NimbusJwtDecoder.withPublicKey`). **No JWKS
  endpoint** in MVP — this maximises `mneme-core`'s independence from `mneme-auth`'s
  availability (the core never fetches anything from auth).
- **`kid` is set on every token** so that switching to JWKS-based distribution later (the
  recorded door) requires no change to already-issued token *shape*.
- For **local development**, a dev keypair is generated and git-ignored, loaded through the
  same "key from the environment" mechanism as production — dev and prod differ in the key
  material's source, never in code.

---

## 11. Non-functional notes

- **Latency.** Login and refresh do one or two indexed lookups plus one bcrypt verify
  (login) or one hash + rotate (refresh). bcrypt strength 12 is the dominant cost on login
  by design (it is meant to be slow); everything else is an indexed primary-key or unique
  lookup. No horizontal scaling; small user base.
- **Secrets.** The signing private key and any cookie/signing secrets come from the
  environment, never git. Passwords and raw refresh tokens never appear in logs.
- **Durability.** The `users` table must survive; sessions are disposable (a lost session
  just forces re-login). Schema is reproducible from Flyway migrations (in git); data is
  protected by PostgreSQL backups kept off-host, chosen at deploy time. A backup that has
  never been restored is not a backup. No HA / replication in MVP.

---

## 12. Rejected / deliberately not done

Recorded so these are not re-litigated or "helpfully" reversed later. Each was considered;
each has a reason.

- **JPA / Hibernate** — rejected in favour of Spring Data JDBC. The schema is two flat
  tables with no object graph; JPA's machinery (lazy loading, dirty checking, identity
  map) is unused, and its `@Entity`-as-aggregate temptation actively fights the layering.
  (`mneme-core` uses JPA; `mneme-auth` diverges deliberately for this reason and, in doing
  so, likely sets the pattern a lighter core would follow.)
- **JWKS endpoint** — not built. Static out-of-band public-key distribution keeps
  `mneme-core` fully independent of `mneme-auth`'s availability (no key fetch, cold or
  warm). JWKS's only real gain is rotation-without-redeploy, which the MVP does not need;
  `kid` in the header keeps the door open non-breakingly.
- **argon2** — not used; bcrypt chosen. Spring's `Argon2PasswordEncoder` requires
  BouncyCastle and its Javadoc warns of an attacker/defender asymmetry in the
  implementation, eroding argon2's theoretical edge on this stack. Recorded upgrade path
  via the `{bcrypt}`/`{argon2}` prefix; migration is free.
- **Symmetric JWT signing (HS256)** — rejected. A shared secret would have to live in both
  `mneme-auth` and `mneme-core`; asymmetric keeps the signing capability solely in auth and
  gives core verify-only.
- **Refresh token as a JWT** — rejected. Refresh must be revocable, which a self-contained
  JWT is not; it is an opaque random string looked up (and revoked) in the database.
- **Salting / slow-hashing the refresh token** — rejected. The token is 256-bit
  high-entropy, so it cannot be brute-forced and gains nothing from a salt or a slow hash;
  a plain SHA-256 allows the indexed lookup `/refresh` needs. (Recorded door: an HMAC with
  a server-side pepper, if defence against a DB-only leak is ever wanted.)
- **Separate `sessions` table** — not now; the `session_id` column carries the lineage.
  Extract only when a per-device "your devices" screen with metadata appears (§6).
- **Absolute refresh expiry** — rejected in favour of a sliding idle window. For a
  daily-study app an absolute cap only evicts active users periodically for no security
  gain; reuse detection, not a timer, is what catches a stolen token.
- **`jti` / individually revocable access tokens** — not added. Access tokens are short
  and un-revocable by design; the refresh layer owns revocation. `jti` would be inert
  weight.
- **CSRF token** — not added. `SameSite=Lax` on a same-site deployment closes the vector.
  Recorded door: if the deployment ever becomes cross-site (`SameSite=None`), a CSRF token
  (or double-submit) must be added back.
- **Roles / scopes / permissions** — out of scope; one kind of user. No authority claims
  in the token.
- **Distinguishing "email not found" from "wrong password" on login** — deliberately
  **not** done. Both yield one indistinguishable `401` to prevent user enumeration. This is
  a hard rule, not a UX oversight (see `AGENTS.md`).

---

## 13. Open / deferred (decide at implementation time, keep consistent)

- **Error responses** — decided in shape: `application/problem+json` (`ProblemDetail`)
  mirroring `mneme-core` one-for-one, with a custom machine-readable `code` inside
  `properties`, `type` = `about:blank`. The exact `code` catalogue (e.g.
  `invalid_credentials`, `email_taken`) is finalised per slice. `/login` uses a single
  `invalid_credentials` for both failure modes (§12).
- **`/register` on an already-registered email** — returns `409` with `email_taken`. This
  is an accepted enumeration compromise: without email verification it cannot be closed
  honestly, and half-measures are not worth it. Recorded door: real protection arrives with
  email verification (out of scope now).
- **Cookie name and exact attributes** — the refresh cookie's name and any `Domain`
  attribute needed for the sibling-subdomain setup are settled during the refresh slice
  against the real deployment domains.
- **Validation placement** — field validation at the edge (Jakarta Validation on request
  DTOs) and/or via value-object construction (`Email`, password bounds) in the domain.
  Decide per field; do not duplicate the same rule both places without reason.
- **Profile shape** — `nickname` plus which settings, and whether settings are columns or a
  single `jsonb`, are decided in the profile slice. Likely `jsonb` to avoid over-modelling.
- **Session/cleanup schedule** — the exact retention for consumed rows and the sweep
  cadence are settled with the logout/maintenance slice.
- **RS256 vs ES256** — RS256 chosen (core-friendly default); ES256 (smaller tokens) is a
  door, not taken. Not revisited unless token size becomes a real constraint.