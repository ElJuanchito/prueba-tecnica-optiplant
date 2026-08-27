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
