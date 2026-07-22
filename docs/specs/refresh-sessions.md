# Spec — slice/refresh-sessions

Feature slice. Introduces refresh tokens and sessions: `/v1/auth/refresh` with L2
rotation (atomic consume + strict reuse detection), the refresh cookie, and sliding
expiry. Register and login now open a session and set the cookie. Branch:
`slice/refresh-sessions`.

## Goal

Turn the access-only auth from the previous slice into a real session: each
register/login opens a per-device `Session` and issues a refresh token (delivered as an
`HttpOnly` cookie); `POST /v1/auth/refresh` exchanges it for a fresh access token,
rotating the refresh token each time. Rotation is **atomic** (exactly one winner under
concurrency) and enforces **strict reuse detection** — a replayed, already-consumed token
revokes the whole session lineage. This is the security core of the service; it is sliced
apart from the bootstrap deliberately so the L2 logic is reviewed on its own.

## In scope

Grouped by concern; the plan decides the file list, following `ARCHITECTURE.md` §2/§4/§6/§8
and the `AGENTS.md` rules.

**Endpoints**
- `POST /v1/auth/refresh` — no body; refresh token read from the cookie. On success **200**
  `{ accessToken }` plus a **rotated** `Set-Cookie`. Any failure (unknown / consumed /
  expired / absent) → **401** (`problem+json`, code `invalid_refresh`).
- **Modify** `POST /v1/auth/register` and `POST /v1/auth/login`: in addition to returning
  `{ accessToken }`, they now open a `Session`, issue the first refresh generation, and set
  the refresh cookie. Response **body is unchanged** (`{ accessToken }` only) — the refresh
  token travels solely in `Set-Cookie`.

**Domain**
- A `Session` concept: one authenticated device's refresh-token lineage, identified by
  `SessionId` (domain-generated UUID), owned by a `UserId`. A session has refresh-token
  **generations**; each `/refresh` consumes the current generation and issues the next in
  the same lineage.
- Value objects: `SessionId`, `RawRefreshToken` (the high-entropy secret, never persisted),
  `TokenHash` (SHA-256 of the raw token; the stored form).
- Ports (declared in the domain, in domain terms): `RefreshTokenGenerator`
  (`generate() → RawRefreshToken`), `TokenHasher` (`hash(RawRefreshToken) → TokenHash`),
  and a `SessionRepository` whose contract supports the L2 flow (below) — opening a
  session, an **atomic consume** of a generation, issuing the next generation, revoking a
  lineage, and looking up by `TokenHash`. Time via the injected `Clock`.

**Application (use cases)**
- **Refresh** (new): the L2 flow —
    1. Hash the incoming raw token; **atomically consume** the matching generation
       (conditional: mark `consumed_at = now` only if currently `consumed_at IS NULL` **and**
       `expires_at > now`).
    2. **Won** (the generation was live and this call consumed it): issue the next generation
       (same `SessionId`, new token, `expires_at = now + idle window`), return a new access
       token and set the rotated cookie.
    3. **Lost / not applicable** (the conditional matched nothing): re-resolve the row to
       classify —
        - no row for that hash → **unknown token** → 401, revoke nothing;
        - row present, `expires_at <= now` → **idle lapse** → revoke the lineage, 401;
        - row present, `consumed_at IS NOT NULL` → **reuse detected** → revoke the lineage,
            401. (This branch also catches the honest concurrent double-submit — see Notes.)
- **Modify Register / Login**: after their existing work, open a `Session` and issue the
  first generation. **Register becomes multi-write** (user + session) and is wrapped in
  `@Transactional`; the refresh rotation's consume-then-insert is one transactional unit
  too. This is the conscious single-annotation compromise from `ARCHITECTURE.md` §4 — not a
  `TransactionRunner`.

**Persistence**
- Flyway migration creating `refresh_tokens`: `id uuid` PK, `user_id uuid not null`,
  `session_id uuid not null`, `token_hash char(64) not null unique`,
  `consumed_at timestamptz null`, `expires_at timestamptz not null`,
  `created_at timestamptz not null`, `updated_at timestamptz not null`. Indexes:
  unique(`token_hash`), (`session_id`). A FK `user_id → users(id)`.
- A `RefreshTokenRecord`, a Spring Data JDBC repository, a `JdbcSessionRepository`
  implementing the domain port, and a hand-written mapper.
- **The atomic consume is an explicit conditional `UPDATE`** (`SET consumed_at = :now WHERE
  token_hash = :h AND consumed_at IS NULL AND expires_at > :now`), whose affected-row count
  decides winner (1) vs not (0). A read-then-write (`SELECT` then `UPDATE`) is **forbidden**
  — see Notes / criterion 9.

**Security / crypto**
- `SecureRandomRefreshTokenGenerator` implementing `RefreshTokenGenerator`: 256 bits from a
  CSPRNG, URL-safe encoded.
- `Sha256TokenHasher` implementing `TokenHasher`: `java.security.MessageDigest` SHA-256,
  hex-encoded, **no salt** (high-entropy input; a deterministic hash allows the indexed
  lookup).

**Web**
- Add the refresh handler to `AuthApi`/`AuthController`; register/login now set the cookie.
- Refresh cookie: `HttpOnly; Secure; SameSite=Lax; Path=/v1/auth/refresh`, set via Spring's
  `ResponseCookie` in the web layer (the raw token reaches the web layer from the use case;
  the domain never touches HTTP).
- Cookie **name** and an optional **`Domain`** attribute (for the sibling-subdomain
  deployment) are configuration, with dev-friendly defaults (host-only cookie in dev).

**Config**
- Refresh **idle window** (default **30 days**) as configuration, alongside the existing
  access-token TTL. Cookie name / domain as above.

## Out of scope (do NOT do these — later slices or other services)

- **No logout** (`POST /v1/auth/logout`) — the next slice. Do not add it, and do not build
  cookie-clearing.
- **No cleanup / sweep.** Consumed and expired rows **accumulate** in this slice. Do NOT
  add a `@Scheduled` job, a `deleteExpired` cron, or any background reclamation — that is
  the maintenance/logout slice. (Immediate deletion of consumed rows is *wrong* here: it
  would destroy reuse detection.)
- **No "logout everywhere" / global revoke.** Reuse detection revokes **only the offending
  lineage**; other sessions of the same user are untouched.
- **No reuse-interval time window / grace heuristic.** Reuse detection is strict (any
  consumed token presented → revoke lineage). Do not add a leeway window.
- **No profile**, no `/v1/profile`.
- **No JWKS**, no change to the access-token contract (`ARCHITECTURE.md` §10) — the access
  token is exactly as issued in the previous slice.
- **No CSRF token** — `SameSite=Lax` on the same-site deployment suffices.

## Acceptance criteria

1. Register **and** login set a refresh cookie (`HttpOnly`, `Secure`, `SameSite=Lax`,
   `Path=/v1/auth/refresh`) and still return only `{ accessToken }` in the body; the raw
   refresh token appears **only** in `Set-Cookie`, never in any response body. A
   `refresh_tokens` row exists with the token's SHA-256 hash, a `session_id`, `consumed_at`
   NULL, `expires_at ≈ now + 30d`.
2. `POST /v1/auth/refresh` with a valid cookie → **200** `{ accessToken }` and a **new,
   different** `Set-Cookie`; the presented generation is now `consumed_at != NULL` and a new
   generation exists in the **same** `session_id` with `consumed_at` NULL.
3. The access token returned by refresh satisfies the §10 contract (verifies against the
   **independently loaded** public key; `sub`/`iss`/`aud`/`iat`/`exp` correct).
4. **Sliding expiry:** the rotated generation's `expires_at = rotation_time + 30d` (moved
   forward from the prior value).
5. **Reuse detection (strict):** presenting an already-consumed refresh token → **401**
   **and** the entire lineage (all rows for that `session_id`) is revoked; a subsequent
   refresh using that lineage's most recent token also fails.
6. **Idle lapse:** presenting a token with `expires_at < now` → **401** and the lineage is
   revoked.
7. **Unknown token** (no matching hash) → **401**, and **no** lineage is revoked (other
   sessions unaffected).
8. **Lineage isolation:** a user with two sessions (two devices) — reuse on session A
   revokes A only; session B still refreshes successfully. (Pins the "revoke only this
   family" decision.)
9. **Atomic rotation under concurrency:** two concurrent `/refresh` calls with the *same*
   valid token yield exactly **one** winner that rotates (one new child generation); the
   family does not silently split into two live branches. The loser is classified as reuse
   (per strict L2) and the lineage is revoked. Verified by an integration test issuing two
   concurrent consumes against real Postgres and asserting a single winner.
10. The raw refresh token is stored **only** as its SHA-256 hash; `grep` finds no
    raw-token column and no log statement emitting the raw token.
11. **Register is multi-write and transactional:** it is annotated `@Transactional`; if the
    session insert fails, the user insert rolls back (no half-committed registration).
12. Domain-purity grep holds: `Session`, the new value objects, and the ports import no
    `org.springframework.*` / `com.nimbusds.*` / `java.security.*` implementation types;
    `SecureRandom` and `MessageDigest` live only in the `infrastructure/security` adapters.
13. Boundary grep: no `logout`, no `@Scheduled`, no `deleteExpired`/sweep, no `/v1/profile`,
    no JWKS, no reuse-window/leeway in the diff.
14. `./gradlew build` is green, and green as seen by the human running it.

## Tests

- **Domain / use-case unit (no Spring; fake ports, fixed `Clock`, fake
  generator/hasher):** refresh happy-path rotation; reuse → revoke lineage; idle lapse →
  revoke; unknown → 401 with no revoke; register/login open a session and issue a
  generation. (The fake generator/hasher are exactly why those are ports — this is where
  the L2 branches are pinned deterministically.)
- **Integration (Testcontainers Postgres):**
    - migration applies; unique(`token_hash`) holds; FK holds.
    - `JdbcSessionRepository` atomic consume returns a **single winner** under two concurrent
      consumes of the same generation (criterion 9).
    - reuse revokes the lineage; idle lapse revokes; lineage isolation across two sessions
      (criterion 8).
    - end-to-end over HTTP: login → refresh → refresh (rotation + sliding expiry), then
      replay of a consumed token → 401 with the lineage dead.

## Notes

- Branch `slice/refresh-sessions` off `develop`; fresh agent session; Sonnet.
- **Atomicity is the crux.** The consume MUST be a single conditional `UPDATE` whose
  affected-row count decides the winner. A `SELECT`-then-`UPDATE` is a bug: under
  concurrency both readers see `consumed_at IS NULL`, both insert children, and the family
  splits into two live branches with **no** detection. Review this specifically.
- **Strict reuse, and the double-submit cost (conscious decision).** Distinguishing an
  honest concurrent double-submit from a real replay would require a reuse-interval time
  window, which is deliberately **not** built (thresholds are bug-prone). The coherent
  consequence: an honest double-submit's loser is treated as reuse and the lineage is
  revoked → a re-login on that device. This is standard OAuth rotation behaviour; the
  proper mitigation is **client-side single-flight** of refreshes in `mneme-web` (one
  in-flight refresh at a time), not server-side heuristics here. The reuse-interval window
  remains a recorded door if this proves too harsh in practice.
- **`@Transactional` enters the application layer here** (register multi-write; refresh
  consume+insert) — the single-annotation compromise from `ARCHITECTURE.md` §4, not yet a
  `TransactionRunner`.
- **Cookie path & logout.** This slice scopes the cookie to `Path=/v1/auth/refresh`. The
  logout slice must reconcile the path so the cookie is also sent to `/v1/auth/logout`
  (widen to `/v1/auth`, or place logout under the refresh path) — flag for that slice, do
  not pre-build it here.
- **Doc drift to reconcile (not this diff):** the `ARCHITECTURE.md` §6 `refresh_tokens`
  sketch omitted `updated_at`, but the `AGENTS.md` audit-timestamp rule mandates it on
  every table; this slice includes `updated_at` (advanced when the row is consumed). Fix
  §6's sketch in a later docs chore.
- Register/Login use-case **return shapes change** (they now also surface the raw refresh
  token for the web layer to set the cookie); their existing tests from the previous slice
  must be updated accordingly.ъ