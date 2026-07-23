# Spec — slice/profile

Feature slice, the last of the auth MVP. Adds `GET`/`PUT /v1/profile` (nickname) and, with
it, the service's **first protected endpoint** — so this slice stands up the resource-server
(incoming access-token validation) that every prior slice deliberately avoided. Branch:
`slice/profile`.

## Goal

A signed-in user can read and set their display **nickname**. Delivering this requires the
first authenticated endpoint, so the slice introduces JWT validation of the incoming access
token (`spring-boot-starter-oauth2-resource-server`): `/v1/profile` requires a valid token
while `/v1/auth/**` stays public. The resulting `SecurityFilterChain` + `NimbusJwtDecoder`
is also the working reference `mneme-core` will mirror when it reimplements its
`Authenticator` (`ARCHITECTURE.md` §8, §10).

## In scope

Grouped by concern; the plan decides the file list, per `ARCHITECTURE.md` §2/§8 and
`AGENTS.md`.

**Endpoints (both authenticated)**
- `GET /v1/profile` → **200** `{ userId, nickname }` (nickname may be `null` if never set).
- `PUT /v1/profile` — body `{ nickname }` → **200** `{ userId, nickname }`.

**Security — the resource server (first appearance)**
- Add `spring-boot-starter-oauth2-resource-server`. This brings `spring-security-web`/
  `-config` in for the first time, which **activates Spring's security auto-configuration**
  — so a `SecurityFilterChain` bean **must** be provided or every endpoint locks down.
- `SecurityFilterChain`: `permitAll` for `/v1/auth/**` and `/actuator/health`;
  `authenticated` for `/v1/profile` (and anything else). Stateless session management; CSRF
  disabled (the API is Bearer-token / stateless; `/v1/profile` uses the `Authorization`
  header, not a cookie).
- A `NimbusJwtDecoder` configured with the **public key** corresponding to the signing key,
  validating **signature + `iss` + `exp`**. It does **not** validate `aud` — see Notes.
- The caller's `UserId` is taken from the validated token's `sub` **at the edge** and passed
  into the use case as a parameter. The domain and application never read a
  `SecurityContext`.

**Domain**
- `User` gains an **optional** `nickname` (`Nickname` value object: trims on construction,
  length **1–50**, case preserved, **not** unique). A behaviour to change it
  (`changeNickname(Nickname, Instant now)`) that advances `updatedAt`.
- `UserRepository.findById(UserId)` is used here — add it to the port + `JdbcUserRepository`
  if the register-login slice did not already.

**Application (use cases)**
- **Get profile**: `(UserId) → ProfileView` (`userId`, `nickname`). Read-only.
- **Update profile**: `(UserId, Nickname) → ProfileView` — load, change nickname, save.
  Single write → **no** `@Transactional`.

**Persistence**
- Flyway migration: `ALTER TABLE users ADD COLUMN nickname text NULL`. **No** settings
  column, **no** unique index on nickname. Existing rows get `NULL`.
- `UserRecord` + the mapper updated to carry `nickname`.

**Web**
- A `ProfileApi` interface + `ProfileController` (contract split per `AGENTS.md`).
- `PUT` request DTO with edge validation: nickname `@NotBlank @Size(min = 1, max = 50)`.
- A `ProfileResponse` (`userId`, `nickname`). Errors as `problem+json`, consistent with the
  existing handler (`validation_failed` for 400).

## Out of scope (do NOT do these)

- **No settings.** No `settings` column, no `jsonb`, no settings endpoint. Deferred to the
  slice where a concrete setting first exists — a column "for the future" with no field is
  speculative.
- **No nickname uniqueness** — no unique constraint or index; two users may share a
  nickname (it is a display name, not a login identifier).
- **No default nickname at registration and no backfill.** Registration is **not** modified;
  nickname is nullable and simply absent until the user sets it.
- **No change to the access-token contract (`ARCHITECTURE.md` §10)** — no multi-audience, no
  new claims. `aud` stays `mneme-core`, validated by consumers, not here (Notes).
- **No JWKS endpoint.** The decoder uses the local public key.
- **No avatar / image** — avatars are files, a separate future service; the MVP profile is
  text nickname only.
- **No "logout everywhere", no cleanup/sweep** — unrelated to this slice.

## Acceptance criteria

1. `GET /v1/profile` with a valid access token → **200** `{ userId, nickname }`; `userId`
   equals the token's `sub`; `nickname` is the stored value, or `null` if never set.
2. `PUT /v1/profile` with a valid token and `{ "nickname": "  Alice  " }` → **200** with
   `nickname` persisted **trimmed and case-preserved** (`"Alice"`); a following `GET`
   returns it; `users.updated_at` advanced.
3. `PUT` with a blank nickname or one longer than 50 chars → **400** `problem+json`; the
   stored nickname is unchanged.
4. **Not unique:** two different users can each set nickname `"Alice"` successfully; there
   is no unique constraint or index on `nickname`.
5. `/v1/profile` **without** a token → **401**; with an expired, malformed, or
   wrong-signature token → **401**.
6. **Issuer validation:** a well-formed token whose `iss` is not `mneme-auth` → **401**.
7. **Public-endpoint regression:** `register`, `login`, `refresh`, and `logout` still work
   with **no** token, and `/actuator/health` is still public — the new
   `SecurityFilterChain` did not lock them down.
8. `UserId` reaches the use case as a parameter extracted from `sub` at the edge; `grep`
   finds no `SecurityContextHolder` (or equivalent) usage in the `domain` or `application`
   packages.
9. A token signed by the real signing key validates; one signed by a **different** key →
   **401** (the decoder uses the public key matching the signer).
10. Domain purity holds: `Nickname` and the `User` change import no framework types; all
    security/JWT types live only in `infrastructure`.
11. Boundary grep: no `settings`/`jsonb`, no nickname unique index, no change to
    `TokenSigner`/§10 claims, no JWKS, no avatar field, no registration change.
12. `./gradlew build` is green, and green as seen by the human running it.

## Tests

- **Domain unit (no Spring):** `Nickname` trims, enforces 1–50, preserves case;
  `User.changeNickname` advances `updatedAt` under a fixed `Clock`.
- **Use-case unit (fake ports):** get returns the stored profile (including the `null`
  nickname case); update changes the nickname and saves.
- **Security / web slice:** `/v1/profile` without a token → 401; with a test-signed token →
  200; wrong-signature and wrong-`iss` tokens → 401; `PUT` validation → 400; and a
  regression that `/v1/auth/**` + `/actuator/health` remain reachable without a token.
- **Integration (Testcontainers Postgres):** the migration adds the `nickname` column;
  end-to-end `login → GET profile (nickname null) → PUT profile → GET profile (set)`; two
  users setting the same nickname both succeed; the public endpoints still respond without a
  token.

## Notes

- Branch `slice/profile` off `develop`; fresh agent session; Sonnet.
- **First protected endpoint — the deliberate footgun, now taken.** Adding
  `oauth2-resource-server` pulls in `spring-security-web`/`-config`, which activates the
  security auto-configuration that locks every endpoint by default. The `SecurityFilterChain`
  bean is mandatory and must `permitAll` `/v1/auth/**` and `/actuator/health`; criterion 7 is
  the guard against getting this wrong. Confirm the exact dependency and config APIs under
  Boot 4.1 / Spring Security 7 in the plan — don't guess.
- **`aud`, resolved (flag if you disagree).** The decoder validates **signature + `iss` +
  `exp`, not `aud`**. `mneme-auth` is the token's **issuer**, not its audience —
  `aud = mneme-core` is the *consumer's* (core's) check. Verifying signature + `iss` proves
  "I issued this token to this user", which is the correct check for auth's own endpoint, and
  it needs **no** change to §10. Recorded door: a third token-consuming service → revisit
  multi-audience.
- This `SecurityFilterChain` + `NimbusJwtDecoder(public key, iss, exp)` is the working
  reference for `mneme-core`'s future `Authenticator` slice — keep it clean and minimal.
- **Nickname is nullable**; the `ALTER` leaves existing rows `NULL`, registration is
  untouched, no backfill runs. Considered and rejected for this slice: defaulting the
  nickname at registration + backfilling (touches a merged slice for no MVP benefit).
- Get/Update are single-write or read-only → **no** `@Transactional`.
- The public verification key should be the one matching the signing key already loaded by
  `NimbusTokenSigner` (derive/share it, or load the same public PEM); tests reuse the fixed
  `test-public-key.pem` from the register-login slice.
- **Doc drift to reconcile (not this diff):** with this slice the auth MVP is complete;
  fold the accumulated `ARCHITECTURE.md` fixes (§6 `refresh_tokens.updated_at`, the §6
  "maintenance/logout slice" phrasing) into one `chore/docs` afterwards.