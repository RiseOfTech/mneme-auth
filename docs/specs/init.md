# Spec — chore/init

Scaffolding chore (not a feature slice). Produces a minimal, runnable Spring Boot
skeleton for `mneme-auth` and nothing else. Branch: `chore/init`.

## Goal

Stand up an empty but runnable `mneme-auth` service: a Spring Boot 4.1 application that
builds green, starts with **no external dependencies** (no database, no key material, no
secrets), and answers its actuator health check. This is the ground every later slice
builds on. It deliberately contains no domain, persistence, security, or business
behaviour — those arrive with the slices that need them.

## In scope

**Build**
- Gradle with the **Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`) and the
  Gradle wrapper checked in.
- **Java 21** toolchain.
- **Spring Boot 4.1** via the Spring Boot Gradle plugin, with dependency management from
  the Boot BOM. The build file pins the exact patch version; this spec names the line, not
  the patch.
- Dependencies limited to: `spring-boot-starter-web`, `spring-boot-starter-actuator`, and
  `spring-boot-starter-test` (test scope). Nothing else.

**Application**
- Group `io.github.ifamneme`; root package `io.github.ifamneme.mneme_auth`.
- A single `@SpringBootApplication` entry-point class, `MnemeAuthApplication`, in the root
  package.
- `application.yaml` — minimal: application name, and (to match the ecosystem) server port
  `8080`. No datasource, no security, no custom properties.
- Actuator exposes `health`; `GET /actuator/health` returns `200 {"status":"UP"}`.

**Repository hygiene**
- `.gitignore` covering Gradle build output (`.gradle/`, `build/`), common IDE files, and
  **secret/key material** (`.env*`, `*.pem`, any local key files) — so the git-ignored dev
  signing key introduced by a later slice can never be committed.

## Out of scope (do NOT add these now)

- **No persistence.** No `spring-boot-starter-data-jdbc`, no PostgreSQL driver, no Flyway,
  no migrations, no `spring.datasource` configuration. (These arrive with the first slice
  that persists — register+login.) Adding a datasource now would also break `bootRun`,
  which must start with no database.
- **No security / JOSE.** No `spring-boot-starter-security`, no
  `spring-security-oauth2-*`, no Nimbus, no `PasswordEncoder`, no key loading, no
  resource-server or filter config.
- **No domain, application, or infrastructure code.** No `User`/`Session`, no value
  objects, no use cases, no ports, no records, no mappers.
- **No empty package skeleton.** Do NOT pre-create `domain/`, `application/`,
  `infrastructure/…` directories or placeholder/`package-info` classes. The layout in
  `ARCHITECTURE.md` §2 materialises when a slice adds a real class — not before.
- **No REST endpoints** beyond what actuator provides. No `*Api` interfaces, no
  controllers.
- **No Docker/Compose, no Testcontainers, no CI config, no OpenAPI generation.** Each is a
  separate chore if and when it is needed.
- **No hand-pinned versions** for anything the Boot BOM already manages.

## Acceptance criteria

1. `./gradlew build` completes green (compiles; tests pass).
2. `./gradlew bootRun` starts the application **with no database and no environment
   secrets set**, and `GET http://localhost:8080/actuator/health` returns
   `200 {"status":"UP"}`.
3. `settings.gradle.kts` and `build.gradle.kts` exist; the build is Kotlin DSL. The Gradle
   wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/…`) is checked in.
4. The Java toolchain is 21 and the Spring Boot plugin is on the 4.1 line.
5. `grep` of `build.gradle.kts` shows **none** of: `data-jdbc`, `flyway`, `postgresql`,
   `starter-security`, `oauth2`, `nimbus`. The only starters are `web`, `actuator`, and
   `test`.
6. Exactly one `@SpringBootApplication` class exists, named `MnemeAuthApplication`, in
   `io.github.ifamneme.mneme_auth`.
7. `src/main` contains no package directory other than the root package and the single
   application class — no `domain`/`application`/`infrastructure` trees.
8. `.gitignore` ignores `build/`, `.gradle/`, `.env*`, and `*.pem` (or equivalent
   key-material patterns).
9. The build is green as seen by the human running it (§ working conventions — "verified
   is the build, not the agent's word").

## Tests

- One test only: the default Spring Boot context-load test
  (`@SpringBootTest` `contextLoads`) confirming the application context starts. Scaffolding
  does not need more; behaviour tests arrive with behaviour.

## Notes

- This is a `chore/*`, not a `slice/*`: it delivers infrastructure, not product behaviour.
  It is intentionally empty of logic.
- Keep the diff tiny. If this chore produces more than the build files, the wrapper, one
  application class, one `application.yaml`, one `.gitignore`, and one context-load test,
  something out-of-scope has crept in.
- The package layout target lives in `ARCHITECTURE.md` §2; do not build it here.
- The RS256 signing-key-from-environment mechanism (`ARCHITECTURE.md` §10) is **not** wired
  here. `bootRun` must succeed with no key present.