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

### Remaining Tasks

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

### Status

19/19 Slice 1 tasks complete. Ready for `sdd-verify`, then Slice 2a apply.
