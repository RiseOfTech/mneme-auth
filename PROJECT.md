# mneme-auth — Project scope

`mneme-auth` owns **identity** in the Mneme system: user registration, login, session
lifecycle (token refresh and logout), and the user's own profile (nickname and minimal
settings). Product scope lives in this file; the authoritative design — token format,
key management, storage, endpoints — lives in `ARCHITECTURE.md`. Read `ARCHITECTURE.md`
before implementing.

It is a separate service alongside `mneme-core` (cards, spaced repetition, public REST
API) and `mneme-web` (browser client). Each user is independent: their own account,
profile, and cards. There is no shared data between users — no groups, no shared decks.

## Why it exists

`mneme-core` today resolves a static, seeded bearer token to a `userId` — a placeholder
with no real users and no way to register. `mneme-auth` replaces that placeholder with
real accounts and issues the tokens `mneme-core` consumes.

## Decisions already made (do not re-litigate)

Settled with the architecture mentor. These are product-level givens; the mechanics live
in `ARCHITECTURE.md`.

- **Access token + refresh token.** On register/login, `mneme-auth` issues a short-lived
  signed access token (JWT) carrying the user's id, plus a longer-lived, revocable
  refresh token. Sessions are per-device: a user may be signed in on several devices at
  once, each session revocable on its own.
- **Local validation in `mneme-core`.** The access token is signed with an asymmetric
  key. `mneme-core` verifies it by signature alone, using the public key — it never
  calls `mneme-auth` on a per-request basis. Auth is involved only at register, login,
  and refresh. This independence is the point of the design.
- **Passwords, MVP-simple.** Login is email + password. Passwords are hashed with a
  modern algorithm, never stored or logged in plaintext. No email verification and no
  password-recovery flow in the MVP — named later concerns, not built now.
- **Profile.** A user has a nickname and minimal settings, owned by `mneme-auth`.

## How the three services relate (integration contract)

- `mneme-web` talks to `mneme-auth` for register / login / refresh / logout. It receives
  an access token and sends it to `mneme-core` as `Authorization: Bearer <jwt>` — exactly
  as it sends the static token today, now a real JWT.
- `mneme-core` verifies the JWT's signature with the shared public key and extracts the
  `userId`. Reimplementing its `Authenticator` port to validate a JWT is a **separate
  slice in `mneme-core`**, tracked there. This project defines only the token contract
  `mneme-core` consumes.
- The refresh token lives strictly between `mneme-web` and `mneme-auth`. `mneme-core`
  knows nothing about it.

`mneme-web` and `mneme-auth` are deployed as sibling subdomains of one site. This is a
requirement, not an accident of deployment: the refresh-token cookie model depends on it
(see `ARCHITECTURE.md`).

## Roadmap (slices)

The service is built in thin vertical slices (see the working conventions). Order:

1. **Registration + login** issuing a working JWT — the thinnest path to a token
   `mneme-core` can verify.
2. **Refresh** — rotate the refresh token, return a new access token.
3. **Logout** — revoke the session.
4. **Profile** — read and update nickname and settings.

Each slice carries its own migration and its own spec under `docs/specs/`.

## Out of scope (MVP)

- No email verification, no password reset / recovery.
- No OAuth / social login / third-party identity providers.
- No roles or permissions beyond "is this a valid user" — one kind of user.
- No multi-factor auth.
- No changes to `mneme-core` — the `Authenticator` reimplementation is a `mneme-core`
  slice. This project defines the token contract only.
- No user-uploaded avatars — avatars are files, a separate future concern. A text
  nickname is enough for the MVP profile.