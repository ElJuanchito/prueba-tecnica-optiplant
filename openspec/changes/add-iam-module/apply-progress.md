# Apply Progress: `add-iam-module`

## Slice 1 — Foundations (PR1) — COMPLETE (19/19 tasks)

Branch: `feat/ep-01-iam-01-foundations` (branches from tracker `feat/ep-01-iam`).
Mode: Standard (strict_tdd: false per `openspec/config.yaml`).

### Completed Tasks

- [x] 1.1 Added `spring-boot-starter-security-oauth2-resource-server` (compile) and
      `spring-boot-starter-security-oauth2-resource-server-test` (test) to `backend/pom.xml`,
      placed next to the existing `spring-boot-starter-security` / `-test` pair.
- [x] 1.2 **[BLOCKING for Slice 2 — RESOLVED]** Ran `./mvnw dependency:go-offline` (clean, no
      errors) then `./mvnw dependency:tree`. Confirmed `spring-security-oauth2-jose:7.1.1` and
      `spring-security-oauth2-resource-server:7.1.1` resolve from
      `spring-boot-starter-security-oauth2-resource-server:4.1.1` — the `security-`prefixed
      starter the proposal chose (BOM `:2748`/`:2753`). The differently-named
      `spring-boot-starter-oauth2-resource-server` (`:2643`) was never added and is not on the
      classpath. Decisive tree fragment:
      ```
      +- org.springframework.boot:spring-boot-starter-security-oauth2-resource-server:jar:4.1.1:compile
      |  \- org.springframework.boot:spring-boot-security-oauth2-resource-server:jar:4.1.1:compile
      |     +- org.springframework.security:spring-security-oauth2-jose:jar:7.1.1:compile
      |     |  +- org.springframework.security:spring-security-oauth2-core:jar:7.1.1:compile
      |     \- org.springframework.security:spring-security-oauth2-resource-server:jar:7.1.1:compile
      ```
- [x] 1.3 **[BLOCKING for Slice 2 — RESOLVED]** Read the real type names off the resolved JARs
      with `javap`/`unzip -l` (never a remembered Boot 3 name). Updated every
      `⟪UNRESOLVED⟫` marker in `design.md`'s `SecurityConfig` block. Confirmed names, for Slice 2
      to use verbatim:

      | Role | Confirmed type |
      |---|---|
      | HMAC decoder | `org.springframework.security.oauth2.jwt.NimbusJwtDecoder` — `NimbusJwtDecoder.withSecretKey(SecretKey)` → `.macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)` → `.build()` |
      | Decoder interface accepted by the DSL | `org.springframework.security.oauth2.jwt.JwtDecoder` |
      | Encoder (slice 2, issuing access tokens) | `org.springframework.security.oauth2.jwt.NimbusJwtEncoder` — `NimbusJwtEncoder.withSecretKey(SecretKey)` |
      | Claim-set builder | `org.springframework.security.oauth2.jwt.JwtClaimsSet` (`JwtClaimsSet.builder()...build()`), wrapped in `JwtEncoderParameters.from(JwtClaimsSet)` |
      | Authentication converter (`.jwtAuthenticationConverter(...)`) | `org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken>` |
      | Bearer filter (auto-installed, not hand-instantiated) | `org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter` |
      | `JwtConfigurer` DSL (verified signatures) | `.decoder(JwtDecoder)` and `.jwtAuthenticationConverter(Converter<Jwt, ? extends AbstractAuthenticationToken>)` on `OAuth2ResourceServerConfigurer.JwtConfigurer` (`spring-security-config-7.1.1.jar`) |

      Full resolved-types table and updated DSL snippet committed to `design.md`'s `SecurityConfig`
      section; the design's "Verification status" table and "Open Questions" were also updated to
      mark resolution.
- [x] 1.4 Added `refresh_tokens` table + 4 indexes to `backend/init-db/01-init-schema.sql`
      (inserted after `idx_users_role`, before the "2. MÓDULO: CATÁLOGO MAESTRO" header) —
      matches design SQL verbatim.
- [x] 1.5 `scripts/validar_esquema.sh`: table-count check `19` → `20`.
- [x] 1.6 Appended the 6 refresh-token checks to section C (`rechaza` ×5, `acepta` ×1), verbatim
      from design.
- [x] 1.7 `CLAUDE.md` Verificación block: "19 invariantes" → "25 invariantes".
- [x] 1.8 `openspec/config.yaml:46`: "Checks 19 invariants" → "Checks 25 invariants".
- [x] 1.9 Added `REFRESH_TOKENS` entity + `USERS ||--o{ REFRESH_TOKENS : "mantiene"` relation to
      the Mermaid diagram in `docs/diagrama_er.md`.
- [x] 1.10 Added `refresh_tokens` entity (inside `package "IAM & Organización"`) + relation
      `users ||--o{ refresh_tokens : "mantiene"` to the PlantUML diagram.
- [x] 1.11 Added the optional "Sesiones revocables" bullet to §1's list; no `RF`/`RNF`/`RN`
      identifier introduced (`validar_trazabilidad.py` unaffected).
- [x] 1.12 Created `shared/security/Role.java` (enum `ADMIN, BRANCH_MANAGER, OPERATOR`, no
      `ROLE_` prefix).
- [x] 1.13 Created `shared/security/AuthenticatedPrincipal.java` (record + `isCorporate()` +
      `mayMutateBranch()`) and `shared/security/PrincipalAccessor.java` (interface), both
      JDK-only imports.
- [x] 1.14 Moved `JwtProperties.java` from the base package to
      `iam/infrastructure/config/JwtProperties.java`; added `accessTtl` (`@DefaultValue("15m")`),
      `refreshInactivity` (`@DefaultValue("8h")`), `refreshAbsolute` (`@DefaultValue("7d")`) as
      `Duration` fields. `application-dev.yml` / `application-prod.yml` `secret:` lines untouched
      — `JWT_SECRET`-without-`=` behavior in `compose.yml` does not regress. No other file
      referenced the old `com.optiplant.inventory.JwtProperties`, so the move was a clean cut.
- [x] 1.15 Created `AuthenticatedPrincipalTest` (5 cases: corporate ADMIN `branchId == null`,
      non-corporate, ADMIN mutates any branch, BRANCH_MANAGER/OPERATOR same-branch-only).
- [x] 1.16 Created `SharedIsFrameworkFreeTest` (ArchUnit `noClasses().that().resideInAPackage(
      shared..).should().dependOnClassesThat().resideInAnyPackage(org.springframework..,
      jakarta.persistence..)`).
- [x] 1.17 Confirmed `ModuleBoundariesTest` is non-vacuous: it now runs against real classes in
      `shared/security/**` and `iam/infrastructure/config/**` (both `shared` and `iam` exist as
      direct subpackages). All 5 rules passed.
- [x] 1.18 `ApplicationContextIT` green after the `JwtProperties` move (full Testcontainers
      Postgres 17 boot, readiness probe returns 200 + `UP`).
- [x] 1.19 Ran all three project validators — see Work Unit Evidence below.

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `backend/pom.xml` | Modified | Added `spring-boot-starter-security-oauth2-resource-server` (compile) + `-test` (test scope) |
| `backend/init-db/01-init-schema.sql` | Modified | Added `refresh_tokens` table + 4 indexes |
| `scripts/validar_esquema.sh` | Modified | Table count `19`→`20`; 6 new checks in section C |
| `CLAUDE.md` | Modified | "19 invariantes" → "25 invariantes" |
| `openspec/config.yaml` | Modified | "Checks 19 invariants" → "Checks 25 invariants" |
| `docs/diagrama_er.md` | Modified | Mermaid `REFRESH_TOKENS` entity + relation; PlantUML `refresh_tokens` entity + relation; optional §1 bullet |
| `openspec/changes/add-iam-module/design.md` | Modified | Resolved every `⟪UNRESOLVED⟫` OAuth2 type name; updated verification-status table and open questions |
| `backend/src/main/java/.../shared/security/Role.java` | Created | Enum, no `ROLE_` prefix |
| `backend/src/main/java/.../shared/security/AuthenticatedPrincipal.java` | Created | Record + `isCorporate()` + `mayMutateBranch()` |
| `backend/src/main/java/.../shared/security/PrincipalAccessor.java` | Created | Interface, JDK-only |
| `backend/src/main/java/.../iam/infrastructure/config/JwtProperties.java` | Created (moved) | 3 new `@DefaultValue` `Duration` fields added |
| `backend/src/main/java/.../JwtProperties.java` | Deleted | Superseded by the moved file |
| `backend/src/test/java/.../shared/security/AuthenticatedPrincipalTest.java` | Created | RN-08/RN-14 truth table |
| `backend/src/test/java/.../SharedIsFrameworkFreeTest.java` | Created | ArchUnit: `shared` imports no Spring/JPA |

### Deviations from Design

None — implementation matches design. The design.md `SecurityConfig` block's `⟪UNRESOLVED⟫`
markers were replaced with confirmed names as part of task 1.3 (this is expected apply work per
the design's own "Rule for apply" note), not a deviation.

### Issues Found

None.

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and result | `cd backend && ./mvnw test -Dtest=AuthenticatedPrincipalTest,SharedIsFrameworkFreeTest,ModuleBoundariesTest` → `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS |
| Runtime harness command and result | `cd backend && ./mvnw verify` (real Testcontainers Postgres 17) → `ApplicationContextIT`: `Tests run: 1, Failures: 0, Errors: 0` (readiness probe 200 `UP`); overall `BUILD SUCCESS` |
| Rollback boundary | Revert this commit; `docker compose down -v` before the next start (the schema-only `refresh_tokens` table has no consumers yet in this slice) |

### Full Verification (task 1.19 / prompt requirement)

All three project validators run and green — decisive output line for each:

1. `./scripts/validar_esquema.sh` (Docker, fresh volume via container-per-run):
   ```
   RESULTADO: 25 comprobaciones correctas — esquema íntegro
   ```
   (`20 tablas creadas (20)` confirmed in section A; all 6 new refresh-token checks in section C
   passed.)
2. `python3 scripts/validar_trazabilidad.py`:
   ```
   RESULTADO: trazabilidad íntegra
     42 RF · 34 RNF · 17 RN · 37 CU · 6 DT
   ```
3. `cd backend && ./mvnw verify`:
   ```
   BUILD SUCCESS
   ```
   (11 surefire tests + 1 failsafe IT, all green.)

### Remaining Tasks (as of Slice 1)

Slice 2a (Login, PR2) and later slices — not started, per explicit instruction to implement
Slice 1 only.

### Workload / PR Boundary

- Mode: chained PR slice (`feature-branch-chain`, `auto-chain` delivery strategy)
- Current work unit: 1. Foundations (PR1)
- Boundary: starts from the pre-existing skeleton (`InventoryApplication`, `JwtProperties` in base
  package, `SecurityConfig` unchanged) and ends with `shared/security/**` created,
  `iam/infrastructure/config/JwtProperties` in place with 3 new durations, `refresh_tokens` table
  + all cross-cutting docs/scripts/config updated, and the two Slice-2-blocking OAuth2 resolution
  tasks closed. `SecurityConfig` itself was intentionally left untouched in the base package —
  its move and OAuth2 wiring belong to task 2b.2/2b.1, not this slice.
- Estimated review budget impact: ~311 authored changed lines (77 in 5 modified tracked files +
  205 in 6 new/moved Java files, minus the 29-line deleted `JwtProperties.java`), under the
  400-line budget and close to the ~290-line estimate in the tasks forecast.

### Status (as of Slice 1)

19/19 Slice 1 tasks complete. Ready for `sdd-verify`, then Slice 2a apply.

---

## Slice 2a — Login (PR2) — COMPLETE (8/8 tasks)

Branch: `feat/ep-01-iam-02a-login` (branches from `feat/ep-01-iam-01-foundations`, itself open as
PR1 against tracker `feat/ep-01-iam`).
Mode: Standard (`strict_tdd: false`).

### Completed Tasks

- [x] 2a.1 Created `iam/domain/model/UserAccount.java` (record: `externalId`, `username`,
      `passwordHash`, `role`, `branchExternalId`, `active`) and
      `iam/domain/exception/{InvalidCredentialsException,UserDisabledException}.java`. Also added
      `TooManyLoginAttemptsException` (not in the design's exception enumeration, but needed for
      RNF-SEC-06 throttling — see Deviations).
- [x] 2a.2 Created port/in `AuthenticateUseCase` (`LoginCommand`/`LoginResult` records) and port/out
      `UserRepositoryPort`, `PasswordHasherPort`, `AccessTokenIssuerPort`, `LoginThrottlePort`, plus
      **`SecretTokenGeneratorPort`** and **`RefreshTokenRepositoryPort`** (see Deviations — the task
      list assigned these to 2b, but login cannot return a refresh token without them).
- [x] 2a.3 Created `application/service/AuthenticationService`: throttle check → load user by
      username → BCrypt match → active check → throttle record → issue access token → generate +
      persist refresh token, `@Transactional`. Deliberately has **no** dependency on `JwtProperties`
      (an `iam.infrastructure.config` type) — `laCapaDeAplicacionNoConoceSusAdaptadores` forbids the
      application layer from importing anything under `..infrastructure..`; TTL/expiry math instead
      lives entirely inside the infrastructure adapters that already need `JwtProperties`
      (`JwtAccessTokenAdapter`, `RefreshTokenPersistenceAdapter`).
- [x] 2a.4 Created `adapter/out/security/{BCryptPasswordHasher,JwtAccessTokenAdapter,
      InMemoryLoginThrottle}.java`. `JwtAccessTokenAdapter` uses the Slice-1-resolved
      `NimbusJwtEncoder.withSecretKey(SecretKey).algorithm(MacAlgorithm.HS256).build()` /
      `JwtClaimsSet` / `JwtEncoderParameters` names verbatim. `InMemoryLoginThrottle` is a
      fixed-window limiter (5 attempts / 5 minutes, keyed `lower(username)+"|"+clientIp`), with a
      package-visible `Clock`-injecting constructor for deterministic tests; production uses
      `Clock.systemUTC()` via the no-arg constructor. Background eviction of stale keys was **not**
      implemented (see Deviations — the design already frames the whole throttle as "openly
      limited"). Also created `SecureRandomTokenGenerator` (256-bit `SecureRandom`,
      Base64 URL-safe) — pre-empted from task 2b.7.
- [x] 2a.5 Created `adapter/out/persistence/{UserJpaEntity,UserSpringDataRepository,
      UserPersistenceAdapter,UserMapper}.java`. No `BranchJpaEntity` exists yet (slice 5b), so a
      user's branch external id / active flag come from two small scalar native queries
      (`findBranchExternalId`, `findBranchActive`) rather than a JPA relation; `UserMapper`
      (MapStruct, `componentModel = "spring"`) combines the entity with those two extra parameters
      into `UserAccount`, computing `active` as `entity.isActive() && (no branch || branch active)`.
      Also created `adapter/out/persistence/{RefreshTokenJpaEntity,
      RefreshTokenSpringDataRepository,RefreshTokenPersistenceAdapter}.java` (persist-only;
      pre-empted from 2b.7/2b.8 — see Deviations). The SHA-256 digest is computed inline in
      `RefreshTokenPersistenceAdapter` for now; the design's `Sha256TokenDigest` class is expected
      to absorb it in slice 2b once `SessionRefreshService` needs the same digest for a lookup.
- [x] 2a.6 Created `adapter/in/web/AuthController.java` — `POST /api/auth/login`. Exception mapping
      stays local to this controller (`@ExceptionHandler` methods) rather than a shared
      `IamExceptionHandler`, since that class is task 2b.9's job once `/refresh` and `/logout`
      exist too. **Deviation** (documented in tasks.md): `UserDisabledException` maps to the exact
      same generic 401 body as `InvalidCredentialsException`, not the task's literal
      "`403`-equivalent" — seeded reasoning: a distinguishable response for "disabled" vs. "wrong
      password" would itself leak that the account exists, defeating CU-SEG-01 EX-01's
      no-existence-leak requirement, which the prompt's settled behaviour explicitly restates
      ("Failed credentials must not reveal whether the username exists"). `SecurityConfig` (still
      in the base package — the move to `iam.infrastructure.config` is task 2b.2) gets exactly one
      added line: `.requestMatchers("/api/auth/login").permitAll()`, with a comment marking the
      rest of the OAuth2/CORS wiring as 2b's job.
- [x] 2a.7 Created `LoginRateLimitTest` (5 cases: below-limit allowed, at-limit blocked, success
      clears the counter, window expiry resets the counter, distinct keys don't interfere) using a
      package-visible `Clock`-injecting constructor — deterministic, no real 5-minute sleep.
- [x] 2a.8 Ran both `cd backend && ./mvnw test` (surefire, 16 tests green) and
      `cd backend && ./mvnw verify` (failsafe, 6 tests green, `BUILD SUCCESS`) — the runtime
      attempt's evidence goal required `AuthenticationFlowIT` login scenarios against real
      Postgres 17, which goes beyond this task's literal "Docker not required" note; see
      Deviations. Created `AuthenticationFlowIT` (in `com.optiplant.inventory`, not under
      `iam.infrastructure.adapter.in.web` — `TestcontainersConfiguration` is package-private, so
      the IT must share its package, matching `ApplicationContextIT`'s existing location) covering:
      successful login for a corporate `ADMIN` (`branchId == null`) and a `BRANCH_MANAGER`
      (`branchId` present); unknown-username and wrong-password both return `401` with an
      **identical** response body; a disabled user (inserted directly via
      `UserSpringDataRepository`, not seed data) returns `401`; 5 consecutive failed attempts
      followed by a 6th return `429`. Slice 2b is expected to extend this exact class with the
      refresh-rotation and logout scenarios (`SecurityConfig`'s bearer chain lands there).

### Deviations from Design / Tasks

1. **Pre-empted refresh-token generation and persistence from slice 2b into 2a** (
   `SecretTokenGeneratorPort`, `RefreshTokenRepositoryPort`, `SecureRandomTokenGenerator`,
   `RefreshTokenJpaEntity`, `RefreshTokenSpringDataRepository`, `RefreshTokenPersistenceAdapter`).
   The task list's 2a.2 only names `UserRepositoryPort`, `PasswordHasherPort`,
   `AccessTokenIssuerPort`, `LoginThrottlePort`, and assigns the refresh-token ports/adapters to
   2b.5/2b.7/2b.8 — but 2a.3 itself says the service must "generate+persist refresh token, per
   design Data Flow LOGIN", and the prompt's settled behaviour is explicit: "Login takes
   credentials and returns an access token plus a refresh token." Those two instructions can't both
   hold without the persistence path existing now. What's still deferred to 2b: lookup-by-hash,
   rotation, reuse-family revocation, and the standalone `Sha256TokenDigest` class (its logic is
   inlined in `RefreshTokenPersistenceAdapter` for the write-only need this slice has).
2. **`TooManyLoginAttemptsException` added**, not in the design's `iam.domain.exception`
   enumeration. `LoginThrottlePort.checkAllowed` needs some way to signal "blocked" to
   `AuthenticationService`; a boolean return would push the 429-vs-401 decision into the service,
   coupling it to HTTP semantics. An exception, thrown by the throttle adapter and caught in
   `AuthController`, keeps that decision in the same place the other two auth exceptions are
   handled.
3. **Disabled user/branch maps to the same generic response as invalid credentials**, not a
   distinguishable `403`-equivalent as task 2a.6 literally says. Rationale in the Completed Tasks
   entry for 2a.6 above; this is a security-hardening choice, not a shortcut — it satisfies the
   prompt's stated hard constraint more completely than the task's literal wording.
4. **No background eviction of stale throttle keys.** `InMemoryLoginThrottle` evicts a key lazily,
   on its own next `checkAllowed`, not via a scheduled sweep. Accepted because the design already
   frames the whole throttle mechanism as "openly limited" (per-instance ceiling) with no DT item
   required; a long-uptime instance with many distinct `username|ip` pairs will grow that map
   until process restart. Flagged here rather than silently dropped.
5. **`TestcontainersConfiguration` (test infra, not owned by any single slice) was extended** to
   copy `backend/init-db/*.sql` into the container's `/docker-entrypoint-initdb.d/`
   (`.withCopyFileToContainer(MountableFile.forHostPath("init-db"), "/docker-entrypoint-initdb.d")`),
   mirroring `compose.yml`'s existing volume mount. **Why this was necessary, found by executing,
   not by reading (CLAUDE.md)**: Slice 1 had zero `@Entity` classes, so Hibernate's
   `ddl-auto: validate` had nothing to check against the bare Testcontainers Postgres container —
   `ApplicationContextIT` passed trivially without any schema ever being loaded into that
   container. The moment this slice added the first two `@Entity` classes
   (`UserJpaEntity`, `RefreshTokenJpaEntity`), `./mvnw verify` failed at context startup with
   `SchemaManagementException: Schema validation: missing table [refresh_tokens]` — proof the
   Testcontainers instance never ran the init scripts. This is a latent gap every future
   entity-backed slice (2b onward) would have hit; fixing it here, the first slice to need it, is
   in scope rather than deferred.
6. **Fixed a pre-existing wrong BCrypt hash in `backend/init-db/02-seed-data.sql`.** The seed file's
   header comment claims `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi` is the
   BCrypt hash of `Password123!`, and all 7 seed users share it. **Verified false by executing**
   `BCryptPasswordEncoder.matches("Password123!", <that hash>)` directly, which returned `false`
   (CLAUDE.md: "cada defecto grave de este repositorio apareció al ejecutar, nunca al leer" —
   textbook case: a hash comment that "looks right" was never actually run against its claimed
   plaintext until this slice's login flow needed to). Nobody had discovered this before because no
   code ever verified a password against these seed rows until now. Generated a fresh, verified
   hash (`$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q`, cost factor 10, unchanged)
   with the same `BCryptPasswordEncoder` and replaced all 8 occurrences (header comment + 7 user
   rows). This is a seed-data value fix, not a schema-structure change — `01-init-schema.sql` is
   untouched, and `./scripts/validar_esquema.sh` (25/25) and `python3 scripts/validar_trazabilidad.py`
   both still pass after the change.

### Issues Found

- The wrong BCrypt hash in seed data (see Deviation 6) — fixed, not merely noted, since it silently
  blocked every future slice's login-dependent IT from ever passing.
- The missing schema initialization in `TestcontainersConfiguration` (see Deviation 5) — fixed for
  the same reason: every future `@Entity`-backed IT would have hit it.

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and result | `cd backend && ./mvnw test -Dtest=LoginRateLimitTest` → green; full `./mvnw test` → `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` |
| Runtime harness command and result | `cd backend && ./mvnw clean verify` (real Testcontainers Postgres 17, schema+seed loaded via the fixed `TestcontainersConfiguration`) → `Tests run: 6, Failures: 0, Errors: 0` (`ApplicationContextIT` 1 + `AuthenticationFlowIT` 5) — `BUILD SUCCESS` |
| Cross-cutting greps | `ROLE_` and `hasRole(` absent from `backend/src` (only doc-comment mentions of the string `ROLE_` itself) |
| `./scripts/validar_esquema.sh` | `RESULTADO: 25 comprobaciones correctas — esquema íntegro` (unaffected by the seed-data fix; still 20 tables) |
| `python3 scripts/validar_trazabilidad.py` | `RESULTADO: trazabilidad íntegra` — `42 RF · 34 RNF · 17 RN · 37 CU · 6 DT` |
| Rollback boundary | Revert this commit; login endpoint disappears (the one `permitAll` line in `SecurityConfig` reverts with it), deny-by-default chain intact. The `TestcontainersConfiguration` and seed-data-hash fixes are load-bearing for *every* later slice's IT and should not be reverted independently of this commit even if the rest of 2a is reverted. |

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `backend/src/main/java/.../iam/domain/model/UserAccount.java` | Created | Domain record |
| `backend/src/main/java/.../iam/domain/exception/{InvalidCredentialsException,UserDisabledException,TooManyLoginAttemptsException}.java` | Created | Auth failure exceptions |
| `backend/src/main/java/.../iam/application/port/in/AuthenticateUseCase.java` | Created | Login use case + command/result records |
| `backend/src/main/java/.../iam/application/port/out/{UserRepositoryPort,PasswordHasherPort,AccessTokenIssuerPort,LoginThrottlePort,SecretTokenGeneratorPort,RefreshTokenRepositoryPort}.java` | Created | Output ports |
| `backend/src/main/java/.../iam/application/service/AuthenticationService.java` | Created | Login orchestration, `@Transactional` |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/security/{BCryptPasswordHasher,JwtAccessTokenAdapter,InMemoryLoginThrottle,SecureRandomTokenGenerator}.java` | Created | Security adapters |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/persistence/{UserJpaEntity,UserSpringDataRepository,UserPersistenceAdapter,UserMapper,RefreshTokenJpaEntity,RefreshTokenSpringDataRepository,RefreshTokenPersistenceAdapter}.java` | Created | Persistence adapters |
| `backend/src/main/java/.../iam/infrastructure/adapter/in/web/AuthController.java` | Created | `POST /api/auth/login` + local exception mapping |
| `backend/src/main/java/.../SecurityConfig.java` | Modified | One added `permitAll` matcher for `/api/auth/login` |
| `backend/src/test/java/.../iam/infrastructure/adapter/out/security/LoginRateLimitTest.java` | Created | Throttle unit tests, fixed `Clock` |
| `backend/src/test/java/.../AuthenticationFlowIT.java` | Created | Login scenarios, real Postgres 17 |
| `backend/src/test/java/.../TestcontainersConfiguration.java` | Modified | Copies `init-db/*.sql` into the container |
| `backend/init-db/02-seed-data.sql` | Modified | Fixed wrong BCrypt hash (8 occurrences) — seed data, not schema |

### Remaining Tasks

Slice 2b (Refresh + Logout, PR3) and later slices — not started, per explicit instruction to
implement Slice 2a only.

### Workload / PR Boundary

- Mode: chained PR slice (`feature-branch-chain`, `auto-chain` delivery strategy)
- Current work unit: 2a. Login (PR2)
- Estimated review budget impact: ~990 authored lines across 19 new files + ~20 changed lines
  across 3 modified files (`SecurityConfig.java`, `TestcontainersConfiguration.java`,
  `02-seed-data.sql`) ≈ **1,010 lines**, well over the 400-line budget this attempt was acquired
  with (~220-line forecast for 2a alone). The overrun is real, not padding: it comes from
  pre-empting the refresh-token write path from 2b (deviation 1, ~150 lines) plus two pre-existing,
  previously-undiscovered defects that had to be fixed for `./mvnw verify` to pass at all
  (deviations 5 and 6). Flagged for the orchestrator/reviewer rather than split after the fact.

### Status

8/8 Slice 2a tasks complete. `cd backend && ./mvnw clean verify` → `BUILD SUCCESS`. Ready for
`sdd-verify`, then Slice 2b apply.

---

## Slice 2b — Refresh + Logout (PR3) — COMPLETE (12/12 tasks)

Branch: `feat/ep-01-iam-02b-refresh-logout` (branches from `feat/ep-01-iam-02a-login`).
Mode: Standard (`strict_tdd: false`).

### Completed Tasks

- [x] 2b.1 Created `iam/infrastructure/config/IamSecurityBeans` (`@Configuration`, two `@Bean`
      methods: `jwtDecoder(JwtProperties)` using the slice-1-resolved
      `NimbusJwtDecoder.withSecretKey(SecretKey).macAlgorithm(MacAlgorithm.HS256).build()`, and
      `iamPrincipalConverter()`). Created `IamPrincipalConverter implements Converter<Jwt,
      AbstractAuthenticationToken>` reading `sub` (user external id), `role`, `branch_id`, and a
      new `username` claim (see Deviations) into a `shared.AuthenticatedPrincipal`, wrapped in a
      new package-private `IamAuthenticationToken extends AbstractAuthenticationToken` so
      `Authentication.getPrincipal()` returns the domain type directly, per design's data flow.
- [x] 2b.2 Moved `SecurityConfig.java` to `iam/infrastructure/config/`; deleted the base-package
      file. Wired `.cors(cors -> cors.configurationSource(...))` against a new `CorsProperties`
      (`optiplant.cors.allowed-origins`, `@NotEmpty List<String>`, no `@DefaultValue` — each
      profile supplies its own list explicitly, mirroring `JwtProperties.secret`) and
      `.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)
      .jwtAuthenticationConverter(iamPrincipalConverter)))`. Kept `permitAll` on
      `/api/auth/login` and `/api/auth/refresh`, `authenticated()` on `/api/auth/logout`.
      **Deliberately did not** add the `/api/admin/**`/`/api/audit/**` authority matchers —
      those are task 3.3, out of this slice's scope per the explicit "do not start Slice 3"
      instruction.
- [x] 2b.3 Created `iam/domain/model/{RefreshTokenGrant,RefreshTokenState,RevocationReason}.java`
      and `iam/domain/exception/RefreshTokenRejectedException.java`. `RefreshTokenGrant` is the
      value object a hash lookup returns (`externalId`, `userExternalId`, `familyId`,
      `issuedAt`/`lastUsedAt`/`expiresAt`, nullable `revokedAt`/`revokedReason`).
      `RefreshTokenState` (`VALID`/`REUSE_DETECTED`/`EXPIRED`/`IDLE_EXPIRED`) is the pure outcome
      of evaluating a grant — a domain concept, not a stored column. `RevocationReason` mirrors
      the DB's four-value CHECK constraint exactly.
- [x] 2b.4 Created `iam/domain/service/RefreshTokenPolicy` — one pure method,
      `evaluate(RefreshTokenGrant, Instant now, Duration idleWindow) -> RefreshTokenState`: revoked
      (presented again) → `REUSE_DETECTED`; `expires_at` not after `now` → `EXPIRED`; idle past
      `lastUsedAt + idleWindow` → `IDLE_EXPIRED`; else `VALID`. No I/O, no `Clock` field — the
      caller passes `now` directly, which is the equivalent of a fixed `Clock` for testing without
      adding a dependency the pure evaluation doesn't need.
- [x] 2b.5 Created `iam/application/port/in/{RefreshSessionUseCase,LogoutUseCase}.java`. Extended
      the existing `RefreshTokenRepositoryPort` (created in 2a, persist-only) with
      `findByRawToken`, `revoke(externalId, reason)`, `revokeFamily(familyId, reason)`.
      `SecretTokenGeneratorPort` already existed from 2a, unchanged. **Added one port beyond the
      literal list**: `RefreshTokenPolicyConfigPort` (`Duration idleWindow()`) — see Deviations for
      why `SessionRefreshService` needs it.
- [x] 2b.6 Created `application/service/SessionRefreshService` (`@Transactional`): lookup by raw
      token → `RefreshTokenPolicy.evaluate` → on `REUSE_DETECTED`, `revokeFamily` then throw; on
      `EXPIRED`/`IDLE_EXPIRED`, throw; on `VALID`, reload the user by `externalId` (role/branch may
      have changed since login), revoke the presented token (`ROTATED`), issue a new access token,
      generate + persist a successor refresh token sharing the same `family_id`. Created
      `application/service/LogoutService` (`@Transactional`): look up by raw token, revoke
      (`LOGOUT`) if found — idempotent no-op otherwise (see Deviations).
- [x] 2b.7 Created `adapter/out/security/Sha256TokenDigest` (`@Component`, one method `hex(String)`)
      and moved the digest logic out of `RefreshTokenPersistenceAdapter`'s inlined private method
      into it, used by both the write path (`persist`) and the new read path
      (`findByRawToken`). Created `SecurityContextPrincipalAccessor implements
      shared.PrincipalAccessor`, reading `SecurityContextHolder`, matching only on
      `AuthenticatedPrincipal` principals. `SecureRandomTokenGenerator` already existed from 2a
      (pre-empted), untouched.
- [x] 2b.8 Extended `RefreshTokenSpringDataRepository` with `findByTokenHash`, and two `@Modifying`
      JPQL updates (`revokeByExternalId`, `revokeByFamilyId`) that set `revoked_at`/`revoked_reason`
      only `WHERE ... revoked_at IS NULL`, so revoking an already-revoked row is a 0-row no-op, not
      an error. Extended `RefreshTokenPersistenceAdapter` to implement `findByRawToken` (hash via
      `Sha256TokenDigest`, resolve `user_id` → `userExternalId` via a new
      `UserSpringDataRepository.findExternalIdById`), `revoke`, `revokeFamily`. Extended
      `UserSpringDataRepository`/`UserPersistenceAdapter`/`UserRepositoryPort` with
      `findByExternalId`, refactoring `UserPersistenceAdapter`'s entity→domain mapping into one
      shared private method used by both `findByUsername` and `findByExternalId`.
- [x] 2b.9 Extended `AuthController` with `POST /api/auth/refresh` (delegates to
      `RefreshSessionUseCase`) and `POST /api/auth/logout` (delegates to `LogoutUseCase`, returns
      `204 No Content`). Extracted the local `@ExceptionHandler` methods (and the `ErrorResponse`
      record) into a new `@RestControllerAdvice(basePackages =
      "...iam.infrastructure.adapter.in.web") IamExceptionHandler`, scoped to this package only so
      it cannot intercept a future module's exceptions. Added a fourth mapping:
      `RefreshTokenRejectedException` → `401`, same generic body shape as the login failures — not
      found / reuse / expired / idle all collapse to one response, for the same
      no-existence/no-state-leak reason CU-SEG-01 EX-01 already requires for login.
- [x] 2b.10 Created `RefreshTokenPolicyTest` (6 cases: valid, reuse/revoked, absolute expiry, idle
      expiry, exactly-at-the-idle-boundary still valid, absolute expiry prevails even with recent
      activity) and `Sha256TokenDigestTest` (determinism, 64-hex-char shape, distinct inputs →
      distinct digests, and a known SHA-256("") test vector confirmed with `sha256sum` before
      writing the assertion — CLAUDE.md "no se afirma nada sin ejecutarlo").
- [x] 2b.11 Extended `AuthenticationFlowIT` with
      `loginLuegoLlamadaProtegidaLuegoRotacionDeRefreshLuegoLogout` covering: login → a "protected
      call" (see Deviations for what stood in for a business endpoint) with and without the bearer
      token → refresh rotates (new refresh token differs; old one now rejects as reuse) → logout
      (`204`, requires the bearer) → the just-logged-out refresh token also rejects. Added
      `refrescarConUnTokenInexistenteDevuelve401` for the not-found branch. The wrong-password and
      disabled-user `401` scenarios already existed from 2a and were not duplicated.
- [x] 2b.12 Grep checks and full verify — see Work Unit Evidence below.

### Deviations from Design / Tasks

1. **Added a `username` claim to the access token** (`JwtAccessTokenAdapter`, created in 2a).
   `IamPrincipalConverter` must rebuild a complete `AuthenticatedPrincipal` from the JWT alone (the
   bearer filter has no session or DB access) — `sub` (external id), `role`, `branch_id` were
   already present, but `username` was not, and `AuthenticatedPrincipal` has no optional/default
   for it. Necessary, minimal, and confined to one adapter already owned by this module.
2. **Added `RefreshTokenPolicyConfigPort`** (`application/port/out`), not in the design's or
   task list's literal enumeration. `SessionRefreshService` needs the configured idle window
   (`JwtProperties.refreshInactivity()`) to call `RefreshTokenPolicy.evaluate(...)`, but
   `JwtProperties` is an `iam.infrastructure.config` type and `laCapaDeAplicacionNoConoceSusAdaptadores`
   forbids the application layer from importing anything under `..infrastructure..` (the same
   constraint 2a's `AuthenticationService` already worked around by pushing all TTL math into
   adapters). A one-method port exposing just the `Duration` is the smallest legitimate way to
   thread a config value across that boundary; implemented by a three-line
   `RefreshTokenPolicyConfigAdapter` wrapping `JwtProperties`.
3. **`LogoutService` is idempotent, not error-throwing, on an unknown/already-revoked token.** The
   design's LOGOUT data flow says "revoke the presented token" but is silent on what happens if it
   doesn't resolve. Treating it as a no-op (not a `401`) keeps logout consistent with the
   no-existence-leak posture the rest of this module holds for authentication endpoints, and
   matches ordinary idempotent-DELETE semantics — a second logout call with the same token is not
   an error.
4. **`AuthenticationFlowIT`'s "protected call" step uses an unmapped path
   (`/api/auth/__protected-probe`) under the bearer chain**, not a dedicated business endpoint.
   No production controller exists yet with an `authenticated()` route (the first one arrives in
   slice 5); adding a throwaway controller only for this IT would be its own scope creep. Asserting
   "not `401`" with a token (Spring MVC's own `404` for "no handler" proves the request passed the
   security filter chain) versus `401` without one proves the decoder/converter/filter wiring
   without depending on a future slice.
5. **`SessionRefreshService` reloads the user by `external_id` on every refresh**, not just using
   the cached principal from the presented token. Not explicitly required by the task list, but
   without it a rotated access token would carry stale role/branch claims for the lifetime of every
   refresh cycle — a correctness gap, not a hardening extra, given `AccessTokenIssuerPort.issue`
   needs a full `AuthenticatedPrincipal`. Did **not** add an active-user check on refresh (that
   remains the disable-flow's job in slice 5a, which revokes all live tokens on disable — a
   redundant check here would be dead code until 5a exists, and is explicitly out of this slice's
   scope).
6. **Loosened the `AuthenticationFlowIT` rotation assertion to not require a distinct access
   token**, found by executing (CLAUDE.md), not by reading: the first version asserted
   `rotado.accessToken() != sesion.accessToken()` and failed, because both tokens were minted
   within the same second in this fast local run — `iat`/`exp` and every other claim can coincide
   at second resolution, producing byte-identical JWTs. Rotation is still proven by the refresh
   token changing and the old one becoming reuse; asserting the access token merely "not blank" is
   what the design's actual guarantee supports.

### Issues Found

None beyond deviation 6 above (a test-assertion defect this slice introduced and fixed within the
same work unit, not a pre-existing one).

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and result | `cd backend && ./mvnw test -Dtest=RefreshTokenPolicyTest,Sha256TokenDigestTest` → `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` |
| Full surefire | `cd backend && ./mvnw test` → `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` (includes `ModuleBoundariesTest` 5/5 and `SharedIsFrameworkFreeTest` 1/1, both still green with the new `iam.infrastructure.config`/`iam.domain.service` classes) |
| Runtime harness command and result | `cd backend && ./mvnw clean verify` (real Testcontainers Postgres 17) → `Tests run: 8, Failures: 0, Errors: 0` (`ApplicationContextIT` 1 + `AuthenticationFlowIT` 7) — `BUILD SUCCESS` |
| Cross-cutting grep — `ROLE_` | `grep -rn "ROLE_" backend/src --include="*.java"` → only three doc-comment mentions (`shared/security/Role.java` ×2, `iam/infrastructure/config/SecurityConfig.java` ×1), all explaining *why not* to use the prefix; no executable use |
| Cross-cutting grep — `hasRole(` in `SecurityConfig` | One doc-comment mention (`nunca {@code hasRole()}`) explaining the same rule; no executable call. `SecurityConfig` uses no authority matchers yet in this slice (those are task 3.3) |
| Rollback boundary | Revert this commit; `/api/auth/refresh` and `/api/auth/logout` disappear, `SecurityConfig` reverts to the 2a state (one `permitAll` line, no bearer chain), login (PR2) unaffected |

### Files Changed

| File | Action | What Was Done |
|------|--------|---------------|
| `backend/src/main/java/.../iam/domain/model/{RefreshTokenGrant,RefreshTokenState,RevocationReason}.java` | Created | Domain value objects |
| `backend/src/main/java/.../iam/domain/exception/RefreshTokenRejectedException.java` | Created | Generic refresh-rejection exception |
| `backend/src/main/java/.../iam/domain/service/RefreshTokenPolicy.java` | Created | Pure grant-validity evaluation |
| `backend/src/main/java/.../iam/application/port/in/{RefreshSessionUseCase,LogoutUseCase}.java` | Created | Use-case ports |
| `backend/src/main/java/.../iam/application/port/out/RefreshTokenPolicyConfigPort.java` | Created | Idle-window config port |
| `backend/src/main/java/.../iam/application/port/out/RefreshTokenRepositoryPort.java` | Modified | Added `findByRawToken`, `revoke`, `revokeFamily` |
| `backend/src/main/java/.../iam/application/port/out/UserRepositoryPort.java` | Modified | Added `findByExternalId` |
| `backend/src/main/java/.../iam/application/service/{SessionRefreshService,LogoutService}.java` | Created | Refresh/logout orchestration |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/security/Sha256TokenDigest.java` | Created | Shared digest, extracted from `RefreshTokenPersistenceAdapter` |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/security/SecurityContextPrincipalAccessor.java` | Created | `shared.PrincipalAccessor` implementation |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/security/JwtAccessTokenAdapter.java` | Modified | Added `username` claim |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/persistence/RefreshTokenSpringDataRepository.java` | Modified | Added `findByTokenHash`, two `@Modifying` revoke queries |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/persistence/RefreshTokenPersistenceAdapter.java` | Modified | Uses `Sha256TokenDigest`; implements lookup/revoke/revokeFamily |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/persistence/UserSpringDataRepository.java` | Modified | Added `findByExternalId`, `findExternalIdById` |
| `backend/src/main/java/.../iam/infrastructure/adapter/out/persistence/UserPersistenceAdapter.java` | Modified | Added `findByExternalId`; shared mapping method |
| `backend/src/main/java/.../iam/infrastructure/adapter/in/web/AuthController.java` | Modified | Added `/refresh`, `/logout`; removed local exception handling |
| `backend/src/main/java/.../iam/infrastructure/adapter/in/web/IamExceptionHandler.java` | Created | Package-scoped `@RestControllerAdvice` |
| `backend/src/main/java/.../iam/infrastructure/config/IamSecurityBeans.java` | Created | `JwtDecoder` + `IamPrincipalConverter` beans |
| `backend/src/main/java/.../iam/infrastructure/config/IamPrincipalConverter.java` | Created | JWT → `AuthenticatedPrincipal` |
| `backend/src/main/java/.../iam/infrastructure/config/IamAuthenticationToken.java` | Created | `AbstractAuthenticationToken` carrying the domain principal |
| `backend/src/main/java/.../iam/infrastructure/config/CorsProperties.java` | Created | `optiplant.cors.allowed-origins` |
| `backend/src/main/java/.../iam/infrastructure/config/RefreshTokenPolicyConfigAdapter.java` | Created | Wraps `JwtProperties.refreshInactivity()` |
| `backend/src/main/java/.../iam/infrastructure/config/SecurityConfig.java` | Created (moved) | Bearer/CORS wiring, `permitAll`/`authenticated()` for `/api/auth/**` |
| `backend/src/main/java/.../SecurityConfig.java` | Deleted | Superseded by the moved file |
| `backend/src/main/resources/application-dev.yml` | Modified | `optiplant.cors.allowed-origins` default (localhost Vite/CRA ports) |
| `backend/src/main/resources/application-prod.yml` | Modified | `optiplant.cors.allowed-origins` from `CORS_ALLOWED_ORIGINS`, no default |
| `backend/src/test/java/.../iam/domain/service/RefreshTokenPolicyTest.java` | Created | 6 pure evaluation cases |
| `backend/src/test/java/.../iam/infrastructure/adapter/out/security/Sha256TokenDigestTest.java` | Created | Determinism, shape, distinctness, known vector |
| `backend/src/test/java/.../AuthenticationFlowIT.java` | Modified | Added full login→refresh→logout scenario + not-found refresh scenario |
| `openspec/changes/add-iam-module/tasks.md` | Modified | Checked off 2b.1–2b.12 |

### Remaining Tasks

Slice 3 (Branch Isolation, PR4) and later slices — not started, per explicit instruction to
implement Slice 2b only.

### Workload / PR Boundary

- Mode: chained PR slice (`feature-branch-chain`, `auto-chain` delivery strategy)
- Current work unit: 2b. Refresh + Logout (PR3)
- Estimated review budget impact: ~922 authored added lines / ~63 deleted lines across
  `backend/src` (per `git diff --numstat`), well over the ~230-line forecast for this slice —
  consistent with 2a's overrun pattern. Most of the volume is genuinely new surface (13 new files:
  domain model/service/exception, two use-case ports, two application services, two security
  adapters, five `iam.infrastructure.config` classes) rather than padding; flagged for the
  orchestrator/reviewer.
- Boundary: starts from 2a's login-only state (`SecurityConfig` still in the base package with one
  `permitAll` line; `RefreshTokenRepositoryPort` persist-only) and ends with the full bearer/CORS
  chain wired, refresh rotation and logout working end-to-end against real Postgres, and the
  digest-extraction/lookup deviations 2a flagged now resolved. Slice 3's ADMIN/audit authority
  matchers were deliberately left out of `SecurityConfig`.

### Orchestrator Review — Two Defects Found and Fixed

Both were found by reading the applied diff before commit, then confirmed by executing.

1. **`RevocationReason.USER_DISABLED` was declared but never emitted.**
   `SessionRefreshService` reloaded the user on every refresh (correct, and deliberate per its
   own deviation note) but never checked `user.active()`. A user disabled — or whose branch was
   deactivated — after login kept rotating refresh tokens until the 7-day absolute window closed.
   That defeats the premise the `refresh_tokens` table exists for: sessions must be revocable.
   `grep -rn USER_DISABLED backend/src` returned exactly one hit, the enum declaration itself,
   while the `revoked_reason` CHECK in `01-init-schema.sql` lists the value — an unused constant
   on both sides of the boundary was the tell. Fixed: on an inactive user, refresh now revokes the
   whole family with `USER_DISABLED` and rejects, closing every device the chain reached.
2. **The Slice-2a seed-hash fix never reached `AuthenticationFlowIT`.**
   The IT declared `SEED_PASSWORD_HASH = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi"`
   with a comment claiming it matched `02-seed-data.sql` — but that is the hash Slice 2a proved by
   execution does *not* correspond to `Password123!`, and the seed file had already been corrected
   to `$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q`. Consequence:
   `unUsuarioDeshabilitadoNoPuedeIniciarSesion` created its user with a hash matching no password,
   so the expected `401` came from a credential mismatch, never from the disabled flag. The test
   was green and proved nothing. Fixed: the constant now carries the corrected hash, with a comment
   stating why drift here silently voids every "login must fail" assertion.

Added `unUsuarioDeshabilitadoDespuesDelLoginNoPuedeRefrescar` to `AuthenticationFlowIT`: it logs in
for real (which only succeeds with the corrected hash, so defect 2 cannot regress unnoticed),
deactivates the user, and requires `401` on refresh (which only holds with defect 1 fixed).

Re-verified after both fixes: `cd backend && ./mvnw clean verify` → `Tests run: 26` (surefire) and
`Tests run: 9` (failsafe, `AuthenticationFlowIT` now 8 cases) → `BUILD SUCCESS`.

### Status

12/12 Slice 2b tasks complete, plus two review defects fixed. `cd backend && ./mvnw clean verify` →
`BUILD SUCCESS`. Ready for `sdd-verify`, then Slice 3 apply.
