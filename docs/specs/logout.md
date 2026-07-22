# Spec — slice/logout

Feature slice. Adds `POST /v1/auth/logout`, which revokes the caller's current session,
and widens the refresh cookie's `Path` to `/v1/auth` so logout can see it. Small slice —
mostly reusing machinery from `slice/refresh-sessions`. Branch: `slice/logout`.

## Goal

Let a signed-in device end its own session: `POST /v1/auth/logout` reads the refresh
cookie, revokes that session's lineage (so its refresh token can no longer be rotated),
clears the cookie, and always returns **204**. It reuses the existing revoke-lineage
mechanism and touches only its own session — other devices stay signed in.

## In scope

**Endpoints**
- `POST /v1/auth/logout` — no body; refresh token read from the cookie. Resolves the
  session for that token and **revokes its lineage** (all rows with that `session_id`),
  using the **same revoke-lineage operation** introduced in `slice/refresh-sessions` (no
  new revocation code path). Returns **204 always**, with a cookie-clearing `Set-Cookie`
  (`Max-Age=0`). Idempotent — it never reveals whether the token was valid.

**Cookie path change (revision of the previous slice)**
- Widen the refresh cookie `Path` from `/v1/auth/refresh` to **`/v1/auth`** at **every**
  place the cookie is set — register, login, and refresh — so the browser also sends it to
  `/v1/auth/logout`. All other attributes are unchanged (`HttpOnly; Secure;
  SameSite=Lax`). The refresh token is still cookie-only, never in a body.
- The logout cookie-clear must match **name + `Path=/v1/auth`** (and domain, if set) of the
  cookie it deletes — a mismatched path would fail to clear it.

## Out of scope (do NOT do these)

- **No cleanup / sweep.** Do NOT add a `@Scheduled` job or any background reclamation of
  consumed/expired rows. Logout (a user-facing revoke) and sweep (background maintenance)
  are separate concerns; sweep is a later chore. Revoked rows may remain until then.
- **No "logout everywhere" / global revoke.** Logout revokes only the current session's
  lineage; other sessions of the same user are untouched. Global sign-out is a future
  feature, not this.
- **No reuse detection on logout.** Logout is not a refresh: it revokes the lineage of
  whatever session the presented token belongs to, **regardless of the token's consumed
  state**, and never returns 401. Do not run the L2 reuse/idle branches here.
- **No profile**, no `/v1/profile`.
- **No change** to the access-token contract (§10) or JWKS.

## Acceptance criteria

1. `POST /v1/auth/logout` with a valid refresh cookie → **204**, empty body, and a
   cookie-clearing `Set-Cookie` (`Max-Age=0`, `Path=/v1/auth`). The session's lineage is
   revoked: a subsequent `/v1/auth/refresh` using that lineage's token → **401**.
2. **Lineage isolation:** a user with two sessions (two devices) — logout on session A
   revokes A only; session B still refreshes successfully.
3. **Idempotent / no validity disclosure:** logout with an absent cookie, an unknown token,
   an already-consumed token, or an expired token **all** return **204** with the
   cookie-clear — never a 401 or any error distinguishing the cases.
4. **No reuse detection:** presenting an already-consumed token to logout revokes that
   token's lineage (via its `session_id`) and still returns 204 — it does not diverge from
   the valid-token path and does not trigger the reuse/revoke-and-401 behaviour.
5. **Cookie path widened everywhere:** register, login, and refresh now set the refresh
   cookie with `Path=/v1/auth`; no `Set-Cookie` in the diff still uses
   `Path=/v1/auth/refresh`. `HttpOnly`, `Secure`, `SameSite=Lax` unchanged.
6. Logout uses the **existing** revoke-lineage operation from `slice/refresh-sessions` — no
   second revocation implementation.
7. Boundary grep: no `@Scheduled`/cleanup/sweep, no global/all-sessions revoke, no
   `/v1/profile`, no access-token or JWKS change in the diff.
8. `./gradlew build` is green, and green as seen by the human running it.

## Tests

- **Use-case unit (no Spring; fake ports):** logout resolves the session by token hash and
  revokes its lineage; absent/unknown token → no-op but still success; a consumed token →
  revokes its lineage via `session_id` (does not run reuse detection).
- **Integration (Testcontainers Postgres):** logout revokes the lineage (subsequent refresh
  → 401); lineage isolation across two sessions; a second logout still returns 204
  (idempotent); end-to-end `login → logout → refresh fails`.
- **Web:** logout returns 204 + cookie-clear for valid, invalid, and absent cookies; the
  widened `Path=/v1/auth` appears on the register/login/refresh cookies.

## Notes

- Branch `slice/logout` off `develop`; fresh agent session; Sonnet.
- The cookie-path widening is a deliberate revision of `slice/refresh-sessions` (which
  scoped the cookie to `/v1/auth/refresh`). It is *why* logout can see the cookie. Change
  every `Set-Cookie` site consistently, and match the clear cookie's path to it.
- Logout is a **single revoke** (one lineage). If revoke-lineage is a single statement
  (`DELETE`/`UPDATE ... WHERE session_id = ?`), logout is a single write → **no**
  `@Transactional` needed. Do not wrap it.
- Follow whatever revoke style `slice/refresh-sessions` chose (hard `DELETE` vs
  soft-revoke) — logout must be consistent with it, not introduce a different one.
- **Doc drift to reconcile (not this diff):** `ARCHITECTURE.md` §6 refers to a combined
  "maintenance/logout slice"; logout (this slice) and the background sweep are separate —
  note for the later docs chore.