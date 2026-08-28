# Tasks: Add IAM Module

> Size note: this checklist deliberately exceeds the skill's default 530-word
> guidance. The task instructions for this change require per-file citations,
> explicit `⟪UNRESOLVED⟫`-blocking flags, and a 7-work-unit forecast across a
> 5-slice, ~1,740-line change — compressing that below 530 words would drop
> required traceability. Kept to one line per task, no prose paragraphs.

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1,740 total (see per-unit table) |
| 400-line budget risk | Medium (High before the pre-approved 2a/2b, 5a/5b split) |
| Chained PRs recommended | Yes |
| Suggested split | PR1 (Slice 1) → PR2 (2a) → PR3 (2b) → PR4 (Slice 3) → PR5 (Slice 4) → PR6 (5a) → PR7 (5b) |
| Delivery strategy | auto-chain |
| Chain strategy | feature-branch-chain |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: feature-branch-chain (user-selected): PR1 targets the tracker branch, each child PR targets the previous one, and only the tracker branch merges to main.
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1. Foundations | schema + shared/security + JwtProperties move, ~290 lines | PR1 | `cd backend && ./mvnw test -Dtest=AuthenticatedPrincipalTest,SharedIsFrameworkFreeTest` | `./scripts/validar_esquema.sh` (real Postgres 17, Docker) + `ApplicationContextIT` | revert commit; `docker compose down -v` before next start (schema-only, no consumers yet) |
| 2a. Login | login domain/app/infra, ~220 lines | PR2 | `cd backend && ./mvnw test -Dtest=LoginRateLimitTest` | `AuthenticationFlowIT` login-only scenarios (Testcontainers Postgres) | revert commit; login endpoint disappears, deny-by-default chain intact |
| 2b. Refresh+Logout | bearer wiring, refresh rotation, logout, ~230 lines | PR3 | `cd backend && ./mvnw test -Dtest=RefreshTokenPolicyTest,Sha256TokenDigestTest` | `cd backend && ./mvnw verify -Dit.test=AuthenticationFlowIT` (full login→refresh→logout, real Postgres) | revert commit; refresh/logout disappear, login (PR2) unaffected |
| 3. Branch isolation | policy + exception handler + authority rules, ~250 lines | PR4 | `cd backend && ./mvnw test -Dtest=BranchAccessPolicyTest` | `cd backend && ./mvnw verify -Dit.test=BranchIsolationIT` | revert commit; authority matchers/handler removed, falls back to `authenticated()` |
| 4. Audit | synchronous write port + query, ~350 lines | PR5 | `cd backend && ./mvnw test -Dtest=AuditEntryCommandTest` | `cd backend && ./mvnw verify -Dit.test=AuditAtomicityIT,AuditLogQueryIT` (real Postgres) | revert commit; `/api/audit` disappears, no downstream slice removed with it |
| 5a. User admin | user CRUD, ADMIN-gated, audited, ~210 lines | PR6 | `cd backend && ./mvnw test -Dtest=UserAdminServiceTest` | `cd backend && ./mvnw verify -Dit.test=UserAdminIT` | revert commit; `/api/admin/users/**` disappears |
| 5b. Branch admin | branch CRUD, ADMIN-gated, audited, ~190 lines | PR7 | `cd backend && ./mvnw test -Dtest=BranchAdminServiceTest` | `cd backend && ./mvnw verify -Dit.test=BranchAdminIT` | revert commit; `/api/admin/branches/**` disappears |

## Phase 1 — Slice 1: Foundations (PR1)

- [x] 1.1 Add `spring-boot-starter-security-oauth2-resource-server` + its `-test` sibling to `backend/pom.xml` (proposal Decision 2; design `:2748,2753` BOM coords).
- [x] 1.2 **[BLOCKING for Slice 2]** Run `cd backend && ./mvnw dependency:tree`; confirm `spring-security-oauth2-jose` resolves from the `security-`prefixed starter, not the `:2643` one; record the output.
- [x] 1.3 **[BLOCKING for Slice 2]** Read the real decoder/encoder/claim-builder/authentication-converter/bearer-filter class names off the resolved JAR in `~/.m2`; replace every `⟪UNRESOLVED⟫` in design.md's SecurityConfig block — never a remembered Boot 3 name (CLAUDE.md).
- [x] 1.4 Add `refresh_tokens` table + 4 indexes to `backend/init-db/01-init-schema.sql` after `:46` (design SQL block) — authentication spec "Refresh token rotation with hashed storage".
- [x] 1.5 Edit `scripts/validar_esquema.sh:78`: table count `19` → `20`.
- [x] 1.6 Append the 6 new refresh-token checks to section C of `scripts/validar_esquema.sh` (after `:94`), matching the design's `rechaza`/`acepta` snippets verbatim.
- [x] 1.7 Edit `CLAUDE.md` Verificación block: "19 invariantes" → "25 invariantes".
- [x] 1.8 Edit `openspec/config.yaml:46`: "Checks 19 invariants" → "Checks 25 invariants".
- [x] 1.9 Add `REFRESH_TOKENS` entity + `USERS ||--o{ REFRESH_TOKENS` relation to the Mermaid diagram in `docs/diagrama_er.md` (near `:70`, entity block after `:82-90`).
- [x] 1.10 Add `refresh_tokens` entity + relation to the PlantUML diagram in `docs/diagrama_er.md` (`:316-317`, relation after `:598`).
- [x] 1.11 Optional: add the "Sesiones revocables" bullet to `docs/diagrama_er.md` §1 (`:10-13`); no `RF`/`RNF`/`RN` id introduced.
- [x] 1.12 Create `com/optiplant/inventory/shared/security/Role.java` (enum `ADMIN, BRANCH_MANAGER, OPERATOR`, no `ROLE_` prefix — CLAUDE.md).
- [x] 1.13 Create `com/optiplant/inventory/shared/security/AuthenticatedPrincipal.java` (record + `isCorporate()` + `mayMutateBranch()`) and `PrincipalAccessor.java` (interface) per design's Interfaces block.
- [x] 1.14 Move `JwtProperties.java` to `iam/infrastructure/config/`; add `accessTtl` (`15m`), `refreshInactivity` (`8h`), `refreshAbsolute` (`7d`) `@DefaultValue` `Duration` fields (P1).
- [x] 1.15 Test: `AuthenticatedPrincipalTest` — RN-08/RN-14 truth table incl. corporate `branchId == null`.
- [x] 1.16 Test: `SharedIsFrameworkFreeTest` — ArchUnit: no `org.springframework..` import under `shared`.
- [x] 1.17 Test: confirm `ModuleBoundariesTest` is non-vacuous now that `shared` exists.
- [x] 1.18 Test IT: `ApplicationContextIT` still green after the `JwtProperties` move.
- [x] 1.19 Run `./scripts/validar_esquema.sh` (expect 20 tables, 25 checks) and `python3 scripts/validar_trazabilidad.py`, then `cd backend && ./mvnw verify`.

## Phase 2a — Slice 2a: Login (PR2)

- [x] 2a.1 Create `iam/domain/model/UserAccount.java`; `iam/domain/exception/InvalidCredentialsException.java`, `UserDisabledException.java`.
- [x] 2a.2 Create port/in `AuthenticateUseCase`; port/out `UserRepositoryPort`, `PasswordHasherPort`, `AccessTokenIssuerPort`, `LoginThrottlePort`. **Extended beyond the literal list**: also added `SecretTokenGeneratorPort` and `RefreshTokenRepositoryPort` — login cannot return a refresh token (per this slice's settled behavior and the spec's "Successful login" scenario) without them; see apply-progress deviations.
- [x] 2a.3 Create `application/service/AuthenticationService` — check throttle → load user (active) → BCrypt match → issue access token → generate+persist refresh token, per design Data Flow "LOGIN".
- [x] 2a.4 Create `adapter/out/security/BCryptPasswordHasher`, `JwtAccessTokenAdapter` (issue only), `InMemoryLoginThrottle` (keyed `lower(username)+"|"+clientIp`, 5/5min, `429`). Also `SecureRandomTokenGenerator` (pre-empted from 2b.7, see deviations).
- [x] 2a.5 Create user persistence: `UserJpaEntity`, `UserSpringDataRepository`, `UserPersistenceAdapter` (implements `UserRepositoryPort`), MapStruct mapper. Also `RefreshTokenJpaEntity`/`RefreshTokenSpringDataRepository`/`RefreshTokenPersistenceAdapter` (persist-only; pre-empted from 2b.7/2b.8, see deviations).
- [x] 2a.6 Create `adapter/in/web/AuthController` — `POST /api/auth/login`, `permitAll`; generic error on bad credentials (no user-existence leak, CU-SEG-01 EX-01). **Deviation**: disabled user/branch maps to the *same* generic 401 as bad credentials, not a distinct `403`-equivalent — a distinct response would itself leak that the account exists but is disabled, which is the same CU-SEG-01 EX-01 leak the task guards against for unknown usernames. `SecurityConfig` (still in the base package; the move is 2b.2) gets one added `permitAll` matcher for `/api/auth/login` only — nothing else from the OAuth2/CORS wiring.
- [x] 2a.7 Test: `LoginRateLimitTest` (throttle window, keyed by username+IP).
- [x] 2a.8 Run `cd backend && ./mvnw test` (surefire, green) **and** `./mvnw verify` (failsafe, green) — the orchestrator's evidence goal for this slice required `AuthenticationFlowIT` login scenarios against real Postgres, beyond this task's literal "Docker not required" scope; see apply-progress.

## Phase 2b — Slice 2b: Refresh + Logout (PR3)

- [x] 2b.1 **[BLOCKED on 1.2/1.3]** Create `iam/infrastructure/config/IamSecurityBeans` hosting the resolved decoder (HMAC from `JwtProperties.secret`) and `IamPrincipalConverter` (maps JWT claims → `Authentication{principal=AuthenticatedPrincipal}`).
- [x] 2b.2 **[BLOCKED on 1.2/1.3]** Move `SecurityConfig.java` to `iam/infrastructure/config/`; wire `.cors()` (new `CorsProperties`, explicit origin list, credentials off) and `.oauth2ResourceServer(rs -> rs.jwt(...))`; keep `permitAll` on login/refresh, `authenticated()` on logout.
- [x] 2b.3 Create `iam/domain/model/RefreshTokenGrant.java`, `RefreshTokenState.java`, `RevocationReason.java`; `iam/domain/exception/RefreshTokenRejectedException.java`.
- [x] 2b.4 Create `iam/domain/service/RefreshTokenPolicy` — validates not-found/revoked(reuse→revoke family)/expired/idle-past-window, per design's REFRESH data-flow branch.
- [x] 2b.5 Create port/in `RefreshSessionUseCase`, `LogoutUseCase`; port/out `RefreshTokenRepositoryPort` (extended, already existed from 2a — see deviations), `SecretTokenGeneratorPort` (already existed from 2a). **Extended beyond the literal list**: also added `RefreshTokenPolicyConfigPort` (out) — see deviations.
- [x] 2b.6 Create `application/service/SessionRefreshService` (one transaction: lookup by raw-token → policy → revoke+insert successor, same `family_id`) and `LogoutService` (revoke presented token only, P4).
- [x] 2b.7 Create `adapter/out/security/Sha256TokenDigest`, `SecurityContextPrincipalAccessor` (implements `shared.PrincipalAccessor`, reads `SecurityContextHolder`). `SecureRandomTokenGenerator` already existed from 2a (pre-empted, per apply-progress).
- [x] 2b.8 Extend refresh-token persistence: `RefreshTokenJpaEntity`/`RefreshTokenSpringDataRepository`/`RefreshTokenPersistenceAdapter` already existed from 2a (persist-only, pre-empted); this task added lookup-by-hash and revoke/revoke-family, and moved the inline SHA-256 digest into `Sha256TokenDigest` (2b.7).
- [x] 2b.9 Extend `AuthController` with `POST /api/auth/refresh` (`permitAll`) and `POST /api/auth/logout` (`authenticated()`); create `IamExceptionHandler` mapping to `401`/`429`.
- [x] 2b.10 Test: `RefreshTokenPolicyTest` (idle / absolute / revoked / reuse, fixed instants), `Sha256TokenDigestTest`.
- [x] 2b.11 Test IT: `AuthenticationFlowIT` — login → protected call → refresh rotates → old token `401` → logout → `401`; wrong password `401` and disabled user `401` (already present from 2a).
- [x] 2b.12 Grep check: `ROLE_` absent under `backend/src` (doc-comment mentions of the literal string aside); `hasRole(` absent in `SecurityConfig` (doc-comment mention aside — see apply-progress). Ran `cd backend && ./mvnw verify` — `BUILD SUCCESS`.

## Phase 3 — Slice 3: Branch Isolation (PR4)

- [x] 3.1 Create `iam/domain/exception/CrossBranchMutationException.java` and `iam/domain/service/BranchAccessPolicy` (wraps `AuthenticatedPrincipal.mayMutateBranch`, throws on `false`) — branch-isolation "Mutations are confined to the caller's own branch".
- [x] 3.2 Extend `IamExceptionHandler`: `CrossBranchMutationException` → `403 Forbidden`.
- [x] 3.3 Add authority matchers to `SecurityConfig`: `/api/admin/users/**`, `/api/admin/branches/**` → `hasAuthority("ADMIN")`; `/api/audit/**` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")` (paths land in slices 4-5; matchers are safe to add now).
- [x] 3.4 Add a test-source-only branch-scoped fixture endpoint exercising `BranchAccessPolicy`, used solely by `BranchIsolationIT` — **flag**: no production business module exists yet with a branch-scoped mutable resource; see Risks.
- [x] 3.5 Test: `BranchAccessPolicyTest` (ADMIN any branch; OPERATOR/BRANCH_MANAGER same-branch only).
- [x] 3.6 Test IT: `BranchIsolationIT` — cross-branch mutation `403`, cross-branch read `200`, ADMIN mutates anywhere, no endpoint accepts a client-supplied `branch_id` (branch-isolation full scenario set).
- [x] 3.7 Run `cd backend && ./mvnw verify`.

## Phase 4 — Slice 4: Audit (PR5)

- [x] 4.1 Create `com/optiplant/inventory/shared/audit/AuditAction.java`, `AuditEntryCommand.java`, `AuditWritePort.java` (JDK-only imports, `sharedEsUnaHoja` holds vacuously) — audit-log "Every mutation writes an audit entry in the same transaction". **Deviation**: `AuditEntryCommand.action` stays `String`, not `AuditAction` — see apply-progress.
- [x] 4.2 Create `iam/domain/model/AuditRecord.java`.
- [x] 4.3 Create `AuditLogJpaEntity`, `AuditLogSpringDataRepository`, `AuditWriteAdapter` (implements `AuditWritePort`; resolves `AuthenticatedPrincipal` UUID → BIGINT via `idx_users_external_id`; `INSERT INTO audit_logs` in the caller's own transaction — CLAUDE.md synchronous-effects invariant).
- [x] 4.4 Create `application/port/out/AuditQueryPort` + query methods on the persistence adapter: filter by user/branch/entity/action/date-range, paginated (RF-SEG-04, RNF-PER-04). **Deviation**: query methods added to `AuditWriteAdapter` itself (the "persistence adapter" task 4.3 already names), not a second class.
- [x] 4.5 Create `application/port/in/QueryAuditLogUseCase` + `AuditQueryService` — ADMIN sees all branches, BRANCH_MANAGER forced to own `branch_id` regardless of submitted filter, OPERATOR denied (enforced by `SecurityConfig`'s slice-3 matcher, not by this service — see apply-progress).
- [x] 4.6 Create `adapter/in/web/AuditLogController` — `GET /api/audit`, paginated, filtered; no update/delete endpoint exists (audit-log "immutable and retained").
- [x] 4.7 Test: `AuditEntryCommandTest`.
- [x] 4.8 Test IT: `AuditLogQueryIT` — the five RF-SEG-04 filters, pagination, role-scoping scenarios.
- [x] 4.9 Test IT: `AuditAtomicityIT` — a use case that throws after `AuditWritePort.record` leaves zero `audit_logs` rows; proves no `@Async`/`AFTER_COMMIT` (load-bearing test, CLAUDE.md).
- [x] 4.10 Run `cd backend && ./mvnw verify`.

## Phase 5a — Slice 5a: User Admin (PR6)

- [x] 5a.1 Create `iam/domain/exception/DuplicateUsernameException.java`.
- [x] 5a.2 Create `application/port/in/ManageUsersUseCase` + `application/service/UserAdminService` — create (unique username+email, role/branch validation), edit (role/branch/profile, `external_id` immutable), disable (`is_active=false` + revoke every live refresh token via `RefreshTokenRepositoryPort`, same transaction), query (no numeric `id` exposed).
- [x] 5a.3 Wire `UserAdminService` mutations through `AuditWritePort` (slice 4) so create/edit/disable each write an audit entry in the same transaction.
- [x] 5a.4 Extend `UserPersistenceAdapter`/`UserJpaEntity`/mapper with save, update, and paginated list-with-filter operations.
- [x] 5a.5 Create `adapter/in/web/UserAdminController` — `POST/PUT/PATCH /api/admin/users/**`, `ADMIN`-gated by `SecurityConfig` (Slice 3); duplicate-username/email → `409`.
- [x] 5a.6 Test: `UserAdminServiceTest` (role/branch validation matrix, duplicate rejection).
- [x] 5a.7 Test IT: `UserAdminIT` — disable revokes every live token (P2/P4); no physical delete; historical movements remain intact.
- [x] 5a.8 Run `cd backend && ./mvnw verify`.

## Phase 5b — Slice 5b: Branch Admin (PR7)

- [x] 5b.1 Create `iam/domain/model/BranchProfile.java`, `iam/domain/exception/DuplicateBranchCodeException.java`.
- [x] 5b.2 Create `application/port/out/BranchRepositoryPort`, `application/port/in/ManageBranchesUseCase`, `application/service/BranchAdminService` — create (unique `code`), edit (name/address/city/phone, `external_id` immutable), disable (`is_active=false`, no delete; disabled branch's users can no longer log in per authentication capability), query (no numeric `id` exposed).
- [x] 5b.3 Wire `BranchAdminService` mutations through `AuditWritePort` (slice 4).
- [x] 5b.4 Create `BranchJpaEntity`, `BranchSpringDataRepository`, `BranchPersistenceAdapter`, MapStruct mapper.
- [x] 5b.5 Create `adapter/in/web/BranchAdminController` — `POST/PUT/PATCH /api/admin/branches/**`, `ADMIN`-gated; duplicate code → `409`.
- [x] 5b.6 Test: `BranchAdminServiceTest` (duplicate code, `external_id` immutability).
- [x] 5b.7 Test IT: `BranchAdminIT` — duplicate `code` `409`; disabling a branch blocks its users' login.
- [x] 5b.8 Run `cd backend && ./mvnw verify`; grep check: no numeric `id` in any response DTO across the module.

## Phase 6 — Cross-Cutting Verification (all slices)

- [ ] 6.1 Run `python3 scripts/validar_trazabilidad.py`, `./scripts/validar_esquema.sh`, `cd backend && ./mvnw verify` together.
- [ ] 6.2 Walk the proposal's Success Criteria checklist end to end (login/refresh/logout flow, cross-branch `403`/`200`, no client-supplied branch id, no `ROLE_`, throttling, disable-revokes-tokens).
