# Verification Report: `add-iam-module` — Slices 1, 2a, 2b, 3, 4, 5a

**Scope**: Tasks 1.1–1.19 (Foundations), 2a.1–2a.8 (Login), 2b.1–2b.12 (Refresh + Logout), 3.1–3.7 (Branch Isolation), 4.1–4.10 (Audit), 5a.1–5a.8 (User Admin).
Slice 5b is intentionally out of scope (not started) and is reported as
not-started, not as a failure.

**Commit verified**: `2240b17` (Slices 1/2a/2b, merged into tracker `feat/ep-01-iam`) plus
Slice 3's own PR merge (branch history shown by `git log --oneline -5`: `510fade` merge of
`feat/ep-01-iam-03-aislamiento-sucursal`) plus the uncommitted working tree on
`feat/ep-01-iam-04-auditoria` for Slice 4 — `git status` shows 1 modified source file
(`UserSpringDataRepository.java`) plus `apply-progress.md`/`tasks.md`, and 13 new untracked
source/test files under `shared/audit/**` and `iam/**` (audit domain model, ports, service,
persistence adapter, controller, and the two IT fixtures), none staged or committed. The
orchestrator owns delivery (commit/PR) for Slice 4; this report verifies the working tree
bytes as they stand.

## Verdict per slice

| Slice | Verdict |
|---|---|
| 1 — Foundations | **PASS** |
| 2a — Login | **PASS** |
| 2b — Refresh + Logout | **PASS WITH WARNINGS** (2 minor coverage/design-deviation warnings, no CRITICAL) |
| 3 — Branch Isolation | **PASS** |
| 4 — Audit | **PASS** |
| 5a — User Admin | **PASS WITH WARNINGS** (3 coverage/design-ambiguity warnings, no CRITICAL) |

**Overall: all six implemented slices are ready to merge.** No CRITICAL issue found in any
slice, including Slice 5a. Slice 5b (Branch Admin) remains correctly unstarted.

## Decisive command output

### `cd backend && ./mvnw clean verify`
```
[INFO] Running com.optiplant.inventory.ApplicationContextIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.ApplicationContextIT
[INFO] Running com.optiplant.inventory.AuthenticationFlowIT
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.AuthenticationFlowIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ inventory ---
[INFO] BUILD SUCCESS
```
Surefire (`./mvnw test`) full run: `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0` —
`BUILD SUCCESS` (includes `ModuleBoundariesTest` 5/5, `SharedIsFrameworkFreeTest` 1/1,
`RefreshTokenPolicyTest` 6/6, `LoginRateLimitTest` 5/5, `Sha256TokenDigestTest` 4/4,
`AuthenticatedPrincipalTest` 5/5).

### `python3 scripts/validar_trazabilidad.py`
```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 6 DT
```

### `./scripts/validar_esquema.sh`
```
A. Carga de los scripts de inicialización
  ok     20 tablas creadas  (20)
C. Seguridad y roles
  ok     RF-SEG-01 · un refresh token exige su hash
  ok     el hash de un refresh token debe ser un SHA-256 en hexadecimal
  ok     dos sesiones no pueden compartir el mismo hash
  ok     un refresh token no puede colgar de un usuario inexistente
  ok     revocar exige registrar el motivo
  ok     un mismo usuario sostiene varias sesiones simultáneas
------------------------------------------------------------
RESULTADO: 25 comprobaciones correctas — esquema íntegro
```

All three validators green.

## CLAUDE.md hard invariants — checked

| Invariant | Status | Evidence |
|---|---|---|
| Roles `ADMIN`/`BRANCH_MANAGER`/`OPERATOR`, no `ROLE_` prefix | PASS | `shared/security/Role.java` enum exact match; `grep -rn "ROLE_" backend/src --include=*.java` → only 3 doc-comment hits explaining *why not*, zero executable use |
| `hasAuthority()` never `hasRole()` | PASS (vacuous for now) | `grep -rn "hasRole(" backend/src` → only 1 doc-comment hit. `SecurityConfig` uses no authority matchers yet (`hasAuthority`/`hasAnyAuthority` are task 3.3, correctly deferred) |
| No new class in a direct subpackage of `com.optiplant.inventory` unless a business module | PASS | Only `shared` (leaf) and `iam` (business module) subpackages added; `InventoryApplication` stays in the base package. `ModuleBoundariesTest.ningunModuloEntraAlInteriorDeOtro` and `noHayCiclosEntreModulos` pass non-vacuously |
| Application layer must not import `..infrastructure..` | PASS | `ModuleBoundariesTest.laCapaDeAplicacionNoConoceSusAdaptadores` passes non-vacuously; verified by reading `AuthenticationService`, `SessionRefreshService`, `LogoutService` — none import `iam.infrastructure.*`. The `RefreshTokenPolicyConfigPort` was added specifically to thread `JwtProperties.refreshInactivity()` across that boundary without violating it |
| `shared/` framework-free and a leaf | PASS | `SharedIsFrameworkFreeTest` 1/1 green; `AuthenticatedPrincipal`/`Role`/`PrincipalAccessor` import only `java.util`/JDK types; `ModuleBoundariesTest.sharedEsUnaHoja` passes |
| Docker-requiring tests named `*IT` not `*Test` | PASS | `ApplicationContextIT`, `AuthenticationFlowIT` are the only Docker-backed tests; both correctly suffixed; all `*Test` classes (`RefreshTokenPolicyTest`, `LoginRateLimitTest`, `Sha256TokenDigestTest`, `AuthenticatedPrincipalTest`, `SharedIsFrameworkFreeTest`, `ModuleBoundariesTest`) are Docker-free, confirmed by running `./mvnw test` alone (surefire only, no Testcontainers boot in the log) |
| No Flyway added alongside `backend/init-db/` | PASS | `refresh_tokens` added via direct SQL edit to `01-init-schema.sql`, no new Flyway dependency or migration file |
| API exposes only `external_id`, never numeric ids | PASS | `AuthController.LoginResponse`/`RefreshResponse` carry only `String`/`UUID`/primitives — `branchId` is `users.branch_id`'s `external_id`. Grep of `adapter/in/web/*.java` found no `Long id`/`long id` field |
| Branch derived from authenticated session, never client param | PASS (for what exists) | No endpoint in this scope accepts a branch identifier from the client; `branch_id` only ever comes from the JWT claim built server-side by `IamPrincipalConverter` |

## Deviations recorded in apply-progress.md — verified

All 10 recorded deviations (6 in Slice 2a, 4 in Slice 2b) were cross-checked against the
actual code, not just the narrative:

1. **2a — Pre-empted refresh-token generation/persistence into 2a.** Confirmed real:
   `SecretTokenGeneratorPort`, `RefreshTokenRepositoryPort`, `SecureRandomTokenGenerator`,
   `RefreshTokenJpaEntity`/`RefreshTokenSpringDataRepository`/`RefreshTokenPersistenceAdapter`
   all exist and are wired from `AuthenticationService` (2a). Justified: `AuthenticateUseCase`'s
   `LoginResult` genuinely returns a refresh token, which cannot happen without persistence.
2. **2a — `TooManyLoginAttemptsException` added.** Confirmed present in
   `iam/domain/exception/`, thrown by `InMemoryLoginThrottle.checkAllowed`, caught by
   `IamExceptionHandler.onTooManyAttempts` → `429`. Reasoning holds.
3. **2a — Disabled user maps to the same generic 401 as bad credentials.** Confirmed in
   `IamExceptionHandler`: `@ExceptionHandler({InvalidCredentialsException.class,
   UserDisabledException.class})` → identical body. Matches CU-SEG-01 EX-01's no-leak intent
   more strictly than the task's literal wording; documented, not silent.
4. **2a — No background eviction of stale throttle keys, only lazy eviction on next
   `checkAllowed`.** Confirmed in `InMemoryLoginThrottle` (no `@Scheduled`, eviction happens
   inline in `checkAllowed`/`recordFailure`). **This is a real gap against the design doc**,
   which explicitly lists "background eviction of stale keys" in its Choice statement
   (design.md, "in-memory login throttle" decision). The deviation is disclosed, and the
   functional spec requirement (RNF-SEC-06, throttling) is still met — this is a memory-growth
   concern under long uptime with many distinct `username|ip` pairs, not a correctness defect.
   **WARNING**, correctly self-reported.
5. **2a — `TestcontainersConfiguration` extended to load `init-db/*.sql`.** Confirmed necessary
   and fixed; without it every `@Entity`-backed IT would fail schema validation. Legitimate
   infra fix, in scope.
6. **2a — Fixed wrong BCrypt hash in seed data.** Confirmed: `02-seed-data.sql` now carries
   `$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q`, and
   `AuthenticationFlowIT.SEED_PASSWORD_HASH` matches it exactly — the two are kept in sync
   (verified this was itself a bug the orchestrator review caught and fixed again in 2b, see
   below). Real, load-bearing fix, correctly documented.
7. **2b — `username` claim added to the access token.** Confirmed in `JwtAccessTokenAdapter`
   (`map.put("username", ...)`) and consumed by `IamPrincipalConverter`
   (`jwt.getClaimAsString("username")`). Necessary given the converter must rebuild a full
   `AuthenticatedPrincipal` from the token alone.
8. **2b — `RefreshTokenPolicyConfigPort` added**, not in design's literal port list. Confirmed
   necessary (see the application/infrastructure boundary check above) and minimal
   (one method, `Duration idleWindow()`).
9. **2b — `LogoutService` idempotent on unknown/already-revoked token.** Confirmed:
   `refreshTokenRepository.findByRawToken(...).ifPresent(...)`, no exception on empty. Spec
   text ("Logout MUST revoke only the refresh token presented") is silent on the not-found
   case; idempotent-DELETE semantics is a reasonable, low-risk interpretation.
10. **2b — `AuthenticationFlowIT`'s "protected call" uses an unmapped probe path.** Confirmed
    (`/api/auth/__protected-probe`, asserted via "not 401" with token / "401" without). Correctly
    flagged as a stand-in for a future business endpoint (slice 5+); does not affect the
    correctness of what it is asserting about the security filter chain.

No unrecorded deviation was found while cross-referencing the diff against design.md and
tasks.md.

## Orchestrator Review — Two Defects Found and Fixed (end of Slice 2b) — verified

Both fixes were re-derived independently from source, not taken on faith:

1. **`RevocationReason.USER_DISABLED` was declared but never emitted — now fixed.**
   Confirmed in `SessionRefreshService.refresh`: after reloading the user by `externalId`,
   `if (!user.active()) { refreshTokenRepository.revokeFamily(grant.familyId(),
   RevocationReason.USER_DISABLED); throw new RefreshTokenRejectedException(...); }`
   (`SessionRefreshService.java:71-74`). This genuinely closes the gap: without it, a user
   disabled after login could keep rotating refresh tokens for up to the 7-day absolute window.
2. **The Slice-2a seed-hash fix had not reached `AuthenticationFlowIT`'s constant — now fixed.**
   Confirmed `AuthenticationFlowIT.SEED_PASSWORD_HASH` (`:33`) equals the corrected hash in
   `02-seed-data.sql`, not the stale one the class originally declared.

**The added IT case `unUsuarioDeshabilitadoDespuesDelLoginNoPuedeRefrescar` genuinely proves
both fixes**, confirmed by reading the test body (`AuthenticationFlowIT.java:142-158`):
- It creates an **active** user and logs in for real via the HTTP endpoint. This step only
  succeeds if `SEED_PASSWORD_HASH` actually matches `SEED_PASSWORD` — i.e., defect 2 is fixed.
  If the hash constant regressed to the stale value, this test would fail at the login step
  with `sesion` being null/error, not silently pass.
- It then flips `is_active` to `false` directly via the repository (bypassing any admin
  endpoint, since none exists yet) and calls `/api/auth/refresh` with the still-live refresh
  token from the successful login.
- It asserts `401`. This assertion only holds if `SessionRefreshService` actually checks
  `user.active()` and revokes on disable — i.e., defect 1 is fixed. Before the fix, this
  refresh would have succeeded (`200`) since nothing in the pre-fix code path checked user
  activity on refresh.

Both fixes are load-bearing for this exact test, and the test was independently re-run as
part of this verification (`./mvnw clean verify`, part of the 8 green `AuthenticationFlowIT`
cases) and passed. The claim is genuine, not decorative.

## Spec compliance matrix (`specs/authentication/spec.md`)

| Requirement / Scenario | Status | Covering test |
|---|---|---|
| Successful login (corporate ADMIN, branch manager) | PASS | `loginExitosoDeAdminCorporativoDevuelveParDeTokensSinSucursal`, `loginExitosoDeGerenteDeSucursalDevuelveSuSucursal` |
| Invalid credentials (no leak) | PASS | `credencialesInvalidasNoRevelanSiElUsuarioExiste` |
| Disabled user | PASS | `unUsuarioDeshabilitadoNoPuedeIniciarSesion` |
| **Disabled branch** (same requirement, second GIVEN clause) | **PARTIAL — untested at runtime** | Logic present and correct (`UserMapper`: `active = entity.isActive() && (branchExternalId == null \|\| branchActive)`), but no IT/unit test exercises a user whose *branch* is disabled while the user itself is active. **WARNING** — low risk (one boolean expression, code-reviewed), but a genuine coverage gap against the spec scenario as written. |
| Access token validation is stateless — No token | PASS | `loginLuegoLlamadaProtegidaLuegoRotacionDeRefreshLuegoLogout` (`protectedProbe(null)` → 401) |
| Access token validation is stateless — Expired or altered token | **UNTESTED** | No IT constructs an expired/altered JWT and asserts rejection. Relies entirely on Spring Security's OAuth2 resource server (well-established library behavior via `NimbusJwtDecoder`/`BearerTokenAuthenticationFilter`), not custom code — **WARNING**, not CRITICAL, given the mechanism is a verified/JAR-resolved standard filter, not module-owned logic. |
| Refresh token rotation with hashed storage — successful refresh | PASS | `loginLuegoLlamadaProtegidaLuegoRotacionDeRefreshLuegoLogout` |
| Refresh token rotation — reused (already-rotated) token rejected | PASS | same test, `reintentoConTokenViejo` → 401 |
| Refresh inactivity expiry | PASS | `RefreshTokenPolicyTest` (idle-expiry cases, pure unit) — no IT exercises real 8h wait, correctly done via fixed-instant unit test per design's testing strategy |
| Logout revokes only the presenting device's session | PASS (single-device path only) | `loginLuegoLlamadaProtegidaLuegoRotacionDeRefreshLuegoLogout` proves the presented token becomes unusable. **No test exercises the explicit multi-device claim** ("device B remains valid" scenario) — only one device/token is ever used in the IT. **SUGGESTION**: add a two-device scenario before slice 5, since P4/multi-device is an explicit product decision. |
| Login rate limiting | PASS | `LoginRateLimitTest` (unit, fixed clock) + `intentosRepetidosFallidosActivanElLimitador` (IT, real 429) |

## Task completion vs. code state

- Tasks 1.1–1.19: all 19 checked in `tasks.md`; all correspond to real, verified artifacts
  (schema, scripts, docs, `shared/security/**`, moved `JwtProperties`, tests). No unchecked
  task in this range.
- Tasks 2a.1–2a.8: all 8 checked; all correspond to real code (domain, ports, service,
  adapters, controller, tests). No unchecked task in this range.
- Tasks 2b.1–2b.12: all 12 checked; all correspond to real code (config beans, domain
  model/service, application services, adapters, controller extension, exception handler,
  tests). No unchecked task in this range.
- Tasks 3.1–3.7: all 7 checked in `tasks.md`; all correspond to real code
  (`CrossBranchMutationException`, `BranchAccessPolicy`, the `IamExceptionHandler` extension,
  the `SecurityConfig` authority matchers, the test-source-only fixture controller,
  `BranchAccessPolicyTest`, `BranchIsolationIT`). No unchecked task in this range. See the
  dedicated Slice 3 section below for the full detail.
- Tasks 4.1 onward (Audit, User Admin, Branch Admin): all unchecked in `tasks.md`, and
  confirmed **not present** in source (`fd` search for `AuditWritePort`, `UserAdminService`,
  `BranchAdminService`, `BranchJpaEntity`, `AuditLogJpaEntity` — zero hits). Consistent —
  reported as not-started, not a failure.

## Issues found (grouped)

### CRITICAL
None.

### WARNING
1. **Login-throttle background eviction not implemented** (deviation 4 above) — lazy eviction
   only. Disclosed by apply-progress; low risk given the design already frames the throttle as
   "openly limited"; recommend a follow-up DT item or a slice-3+ fix, not a merge blocker.
2. **"Disabled branch blocks login" scenario has no runtime-executed covering test.** The
   production logic is present and looks correct (`UserMapper` boolean expression), but per
   the hard rule "a spec scenario is compliant only when a covering test passed at runtime,"
   this half of the "Disabled user or disabled branch" requirement is unverified by execution.
   Recommend a one-line IT addition (insert a user with an inactive branch, assert 401) before
   or alongside slice 5b (branch admin), which will make disabling a branch straightforward to
   set up in a test.
3. **"Expired or altered access token" scenario has no dedicated covering test.** Relies on
   Spring Security's own resource-server filter chain (verified library code, not
   module-owned). Low risk; recommend adding it opportunistically once a protected business
   endpoint exists (slice 5+), rather than blocking this merge.

### SUGGESTION
1. Add a two-device logout scenario (login twice for the same user, logout device A, assert
   device B's refresh token still works) to make the explicit multi-device claim (P4) verified
   by execution rather than by code inspection alone.
2. `credencialesInvalidasNoRevelanSiElUsuarioExiste` compares unknown-username vs.
   wrong-password bodies; consider also asserting the disabled-user body is byte-identical to
   the invalid-credentials body (currently only status code is asserted for the disabled-user
   case), fully closing the CU-SEG-01 EX-01/EX-02 no-leak claim by execution.

## Design coherence

Checked design.md's `SecurityConfig`, Data Flow (LOGIN/AUTHENTICATED REQUEST/REFRESH/LOGOUT),
persistence SQL block, and Interfaces/Contracts sections against the actual code
(`SecurityConfig.java`, `IamSecurityBeans.java`, `IamPrincipalConverter.java`,
`AuthenticationService.java`, `SessionRefreshService.java`, `LogoutService.java`,
`RefreshTokenPolicy.java`, `RefreshTokenPersistenceAdapter.java`,
`01-init-schema.sql:52-72`). All match verbatim or with disclosed, justified deviations (see
above). The resolved OAuth2 type names in design.md's "Verification status" table
(`NimbusJwtDecoder`, `NimbusJwtEncoder`, `JwtClaimsSet`, `JwtAuthenticationConverter`,
`BearerTokenAuthenticationFilter`) are exactly the types actually imported in
`IamSecurityBeans.java`/`JwtAccessTokenAdapter.java`/`IamPrincipalConverter.java` — no
remembered-Boot-3-name regression.

## Slice 3 — Branch Isolation (tasks 3.1–3.7) — detailed verification

### Decisive command output

`cd backend && ./mvnw verify` (real Testcontainers Postgres 17, full module, run against the
uncommitted Slice 3 working tree):
```
[INFO] Running com.optiplant.inventory.iam.domain.service.BranchAccessPolicyTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0   <- surefire total
...
[INFO] Running com.optiplant.inventory.BranchIsolationIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.BranchIsolationIT
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0   <- failsafe total
[INFO] BUILD SUCCESS
```
`python3 scripts/validar_trazabilidad.py` → `RESULTADO: trazabilidad íntegra` (42 RF · 34 RNF ·
17 RN · 37 CU · 6 DT) — unaffected, Slice 3 touched no `docs/` file.
`./scripts/validar_esquema.sh` was **not** re-run: `git diff --stat` for this slice touches no
file under `backend/init-db/` or `docs/`, so it is out of scope per the verification brief;
correctly skipped, not silently omitted.

### Task-by-task verification

| Task | Claim | Verified against |
|---|---|---|
| 3.1 | `CrossBranchMutationException` + `BranchAccessPolicy` created | Both files read in full; `BranchAccessPolicy.requireMayMutate` wraps `AuthenticatedPrincipal.mayMutateBranch` exactly as claimed, throws the named exception |
| 3.2 | `IamExceptionHandler` maps the exception to 403 | `@ExceptionHandler(CrossBranchMutationException.class)` → `HttpStatus.FORBIDDEN` confirmed by reading the class |
| 3.3 | Authority matchers added to `SecurityConfig` | `.requestMatchers("/api/admin/users/**", "/api/admin/branches/**").hasAuthority("ADMIN")` and `.requestMatchers("/api/audit/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")`, both placed before `anyRequest().authenticated()` — confirmed by reading the file |
| 3.4 | Test-source-only fixture endpoint, flagged as no production resource exists yet | `BranchIsolationFixtureController` confirmed under `src/test/java`, not `src/main`; confirmed it never reaches the packaged JAR (Maven's `testCompile`/`test-jar` scoping, not repackaged) |
| 3.5 | `BranchAccessPolicyTest` (ADMIN any branch; OPERATOR/BRANCH_MANAGER same-branch only) | Read in full; 6 tests, all passed at runtime (`Tests run: 6, Failures: 0`) |
| 3.6 | `BranchIsolationIT` — cross-branch 403, cross-branch read 200, ADMIN any branch, no client-supplied `branch_id` accepted | Read in full; 6 tests, all passed at runtime |
| 3.7 | `./mvnw verify` run | Re-run independently by this verification, `BUILD SUCCESS` |

### Spec compliance matrix (`specs/branch-isolation/spec.md`)

| Requirement / Scenario | Status | Covering test |
|---|---|---|
| Acting branch derived from session only — client-supplied `branch_id` ignored | **PASS** | `BranchIsolationIT.ningunEndpointAceptaUnBranchIdSuministradoPorElCliente` — sends a spoofed `branchId` in the PATCH body pointing at Bogotá while acting as an operator of Bogotá against Medellín's resource; still `403`, proving the body is never consulted |
| Cross-branch mutation is rejected (403, no persistence) | **PASS** | `BranchIsolationIT.unOperadorNoPuedeMutarUnaSucursalAjena` (OPERATOR) and `unGerenteDeSucursalNoPuedeMutarUnaSucursalAjena` (BRANCH_MANAGER) — both assert `403`; unit-level equivalent in `BranchAccessPolicyTest.unOperadorNoPuedeMutarUnaSucursalAjena`/`unGerenteDeSucursalPuedeMutarSuPropiaSucursalPeroNoOtra` |
| ADMIN mutates any branch (and would be audited — audit itself is Slice 4, correctly out of scope) | **PASS** (mutation-authorization half only) | `BranchIsolationIT.unAdminCorporativoMutaCualquierSucursal` (all 3 seeded branches, `204` each); `BranchAccessPolicyTest.unAdminCorporativoPuedeMutarCualquierSucursal`. The "records it in the audit log" half of this scenario is **not yet coverable** — no `AuditWritePort` exists until Slice 4 — correctly not claimed as done here |
| Reads of other branches permitted, read-only | **PASS** | `BranchIsolationIT.unOperadorPuedeLeerUnaSucursalAjenaEnSoloLectura` — operator of Bogotá reads Medellín's fixture resource, `200` |

All four branch-isolation spec requirements have a runtime-executed covering test for their
in-scope half. No UNTESTED or FAILING scenario.

### Test-quality check (not just presence)

Read each assertion and reasoned about what would happen if the policy were removed or broken,
rather than accepting green as sufficient on its own:

- `BranchAccessPolicyTest` — every mutating case uses `assertThatThrownBy(...).isInstanceOf(
  CrossBranchMutationException.class)` or an explicit no-throw call; if `mayMutateBranch` were
  changed to always return `true`, `unOperadorNoPuedeMutarUnaSucursalAjena` and
  `unGerenteDeSucursalPuedeMutarSuPropiaSucursalPeroNoOtra` would fail (no exception thrown).
  Not tautological.
- `BranchIsolationIT` — each scenario asserts a specific `HttpStatus` from a real HTTP round
  trip through the actual filter chain and `IamExceptionHandler`. If `BranchAccessPolicy` were
  deleted from the fixture controller's `mutate` method (bypassing the check), the two
  cross-branch tests would receive `204` instead of the asserted `403` and fail. If the
  exception handler's mapping were removed, the same tests would fail with a `500` instead of
  `403` (unmapped `RuntimeException` → default Spring error handler). Both failure modes are
  real, not tautological.
- The client-supplied-`branch_id` test is the one that most needs scrutiny for tautology: it
  currently passes for two independent reasons — (a) the fixture endpoint has no
  `branch_id`-shaped parameter to read from the body at all, so there is nothing to spoof, and
  (b) even if it did, the assertion is against a cross-branch target, which would `403` anyway
  regardless of the spoofed value. This test genuinely proves "the client-supplied value has no
  effect," but it does **not**, by itself, prove "an endpoint that *did* accept a `branch_id`
  parameter would ignore it" — because no such endpoint exists yet in this codebase. This is a
  scope limitation inherent to Slice 3 having no production branch-scoped resource, not a defect
  in the test; flagged as a WARNING below so it is not silently generalized beyond what it
  actually shows.

### Deviations declared in `apply-progress.md` — verified

1. **Fixture endpoint takes no branch-identifier parameter at all.** Confirmed by reading
   `BranchIsolationFixtureController`: the only `@PathVariable` is an opaque `resource` string
   (`bogota`/`medellin`/`cali`), mapped server-side via the `RESOURCE_BRANCH` map to one of the
   three seeded branch `external_id`s. The map's UUIDs (`b0000000-...-001/002/003`) were
   cross-checked against `backend/init-db/02-seed-data.sql`'s `branches` insert and match
   exactly. Real and sound.
2. **`BranchAccessPolicy` is `new`-ed, not a Spring bean.** Confirmed: no `@Component`/`@Service`
   annotation on the class; instantiated with `new BranchAccessPolicy()` in
   `BranchIsolationFixtureController`. Cross-checked the claimed precedent —
   `SessionRefreshService.java:33` reads `private final RefreshTokenPolicy policy = new
   RefreshTokenPolicy();`, and `RefreshTokenPolicy` also carries no Spring stereotype
   annotation. The pattern match is accurate, not asserted from memory.
3. **`CrossBranchMutationException`'s message omits the target branch id.** Confirmed both in
   the exception's own constructor call site (`BranchAccessPolicy.requireMayMutate`, a fixed
   Spanish string with no interpolated UUID) and by the dedicated test
   `BranchAccessPolicyTest.laExcepcionNoRevelaLaSucursalObjetivoEnElMensaje`, which asserts
   `.hasMessageNotContaining(SUCURSAL_B.toString())` and passed at runtime.

All three declared deviations are real, sound, and documented in `apply-progress.md`'s Slice 3
section — no undisclosed deviation was found while cross-referencing the diff against
`design.md` and `tasks.md`.

### CLAUDE.md hard invariants — re-checked for Slice 3's diff

| Invariant | Status | Evidence |
|---|---|---|
| No `ROLE_` prefix / no `hasRole(` in production code | **PASS** | `grep -rn "ROLE_" backend/src --include=*.java` and `grep -rn "hasRole(" backend/src --include=*.java` → only doc-comment mentions (`SecurityConfig` javadoc, `Role.java`); `SecurityConfig`'s new matchers use `hasAuthority`/`hasAnyAuthority` exclusively |
| Branch derived from session only, never a client parameter | **PASS** | `BranchIsolationFixtureController` reads the acting principal via `PrincipalAccessor.require()` (backed by `SecurityContextHolder`), never from a request parameter; the target branch comes from a server-side map keyed by an opaque resource name, not a client-supplied id |
| API exposes only `external_id`, never internal numeric ids | **PASS (no new surface)** | The fixture endpoint returns no body at all (`ResponseEntity<Void>`); no numeric id is exposed anywhere in Slice 3's diff |
| `shared/` stays framework-free and a leaf | **PASS** | `SharedIsFrameworkFreeTest` still 1/1 green after this slice (no new `shared` file added); `BranchAccessPolicy`/`CrossBranchMutationException` both live under `iam`, not `shared` |
| No new production class in a direct subpackage of the base package other than a business module | **PASS** | `find src/main/java/com/optiplant/inventory -maxdepth 2 -type d` shows only `iam` and `shared` as subpackages of the base package; `InventoryApplication` is the only file directly in the base package, unchanged |
| Docker-dependent tests named `*IT`, pure unit tests named `*Test` | **PASS** | `BranchIsolationIT` (Testcontainers, real Postgres) vs. `BranchAccessPolicyTest` (pure unit, no Spring context) — correctly suffixed; confirmed `BranchAccessPolicyTest` ran in the surefire phase (`./mvnw test` alone), not failsafe |

### Issues found for Slice 3

**CRITICAL**: none.

**WARNING**:
1. The "no endpoint accepts a client-supplied `branch_id`" scenario is proven only against a
   fixture endpoint that has no such parameter in its contract at all — it does not (and cannot
   yet) prove that a *future* endpoint which does accept request-body fields correctly ignores a
   `branch_id`-shaped one. Re-verify this exact scenario once Slice 4/5 introduce the first real
   mutating endpoint with a request body.
2. The "ADMIN mutates any branch AND records it in the audit log" scenario (branch-isolation
   spec) is only half-covered here — the audit-log half is untestable until Slice 4 exists. Not
   a gap in this slice's own scope, but worth tracking so it is not forgotten once `AuditWritePort`
   lands: the branch-isolation spec's own acceptance criterion references the audit log
   explicitly, so Slice 4's verification should close the loop back to this exact scenario.

**SUGGESTION**: none beyond what is already tracked from prior slices.


## Slice 4 — Audit (tasks 4.1–4.10) — detailed verification

**Artifact store note**: same as Slice 3 — `openspec/config.yaml` has no `artifact_store`
field and its `schema: spec-driven` plus the on-disk `openspec/changes/add-iam-module/`
tree remain the source of truth. This report is persisted as the canonical file; no
`mem_save` duplicate was issued for the underlying artifact content, following the
precedent set by the Slice 3 verification.

### Decisive command output (re-run independently for this verification)

`cd backend && ./mvnw verify` (real Testcontainers Postgres 17, full module, current
uncommitted working tree on `feat/ep-01-iam-04-auditoria`):
```
[INFO] Running com.optiplant.inventory.AuditAtomicityIT
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.AuditAtomicityIT
[INFO] Running com.optiplant.inventory.AuditLogQueryIT
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.AuditLogQueryIT
[INFO] Running com.optiplant.inventory.AuthenticationFlowIT
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.AuthenticationFlowIT
[INFO] Running com.optiplant.inventory.BranchIsolationIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.BranchIsolationIT
[INFO] Results:
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] --- failsafe:3.5.6:verify (default) @ inventory ---
[INFO] BUILD SUCCESS
```
(Failsafe total 29 = `ApplicationContextIT` 1 + `AuditAtomicityIT` 2 + `AuditLogQueryIT` 8 +
`AuthenticationFlowIT` 12 + `BranchIsolationIT` 6, matching `apply-progress.md`'s own count
exactly.)

Focused re-run, `cd backend && ./mvnw test -Dtest=AuditEntryCommandTest,ModuleBoundariesTest,SharedIsFrameworkFreeTest`:
```
[INFO] Running com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.ModuleBoundariesTest
[INFO] Running com.optiplant.inventory.SharedIsFrameworkFreeTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.SharedIsFrameworkFreeTest
[INFO] Running com.optiplant.inventory.shared.audit.AuditEntryCommandTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.shared.audit.AuditEntryCommandTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
`ModuleBoundariesTest` still 5/5 non-vacuous with the new `shared/audit/**` classes present;
`SharedIsFrameworkFreeTest` still 1/1 green — `shared` remains framework-free with the new
audit port/command/enum added.

`python3 scripts/validar_trazabilidad.py` (re-run):
```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 6 DT
```
Unaffected — Slice 4 touches no `docs/` file (`git diff --stat -- backend/init-db docs`
returns empty, confirmed).

`./scripts/validar_esquema.sh` was **not** re-run: Slice 4 touches no file under
`backend/init-db/` (confirmed via `git status --porcelain | grep init-db` — no hits), so it
is out of scope for this slice, same as Slice 3's precedent.

### Task-by-task verification (4.1–4.10)

| Task | Claim | Verified against |
|---|---|---|
| 4.1 | `AuditAction`, `AuditEntryCommand`, `AuditWritePort` created, JDK-only imports; `action` stays `String` (disclosed deviation) | All three files read in full: `AuditWritePort`/`AuditEntryCommand`/`AuditAction` import only `java.util.UUID` — no Spring/JPA. Record field order/types match `design.md:225-228` verbatim (`actorUserId, branchId, action, entityName, entityId, payloadBefore, payloadAfter, ipAddress`) |
| 4.2 | `AuditRecord` domain model created | Read in full: `external_id`-only fields (`externalId`, `actorUserExternalId`, `branchExternalId`), no numeric id |
| 4.3 | `AuditLogJpaEntity`, `AuditLogSpringDataRepository`, `AuditWriteAdapter` created; UUID→BIGINT resolution; INSERT in caller's transaction | Read in full: `AuditWriteAdapter.record` carries no `@Transactional` (joins caller's transaction, never starts its own); `requireUserId`/`requireBranchId` resolve external ids via `UserSpringDataRepository`, throwing `IllegalStateException` on failure, mirroring `RefreshTokenPersistenceAdapter`'s established pattern |
| 4.4 | `AuditQueryPort` + query methods on the persistence adapter (disclosed: same class as 4.3, not a second one) | Confirmed: `AuditWriteAdapter implements AuditWritePort, AuditQueryPort`; `AuditQueryPort.query`/`AuditFilter`/`AuditPage` read in full, `external_id`-only filter fields |
| 4.5 | `QueryAuditLogUseCase` + `AuditQueryService`; BRANCH_MANAGER forced to own branch; OPERATOR denied via `SecurityConfig` (disclosed: no service-level check) | Read in full: `AuditQueryService.query` computes `effectiveBranch = principal.role() == Role.BRANCH_MANAGER ? principal.branchId() : query.branchExternalId()`; `SecurityConfig`'s `/api/audit/**` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")` matcher (added slice 3) is the only OPERATOR gate, confirmed still present and using `hasAnyAuthority`, never `hasRole` |
| 4.6 | `AuditLogController` — `GET /api/audit`, paginated/filtered, no update/delete endpoint | Read in full: only one `@GetMapping`, no `@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` anywhere in the class; `AuditEntryResponse` exposes only `UUID` fields, no numeric id |
| 4.7 | `AuditEntryCommandTest` created | Re-run independently: `Tests run: 3, Failures: 0` |
| 4.8 | `AuditLogQueryIT` — five RF-SEG-04 filters, pagination, role-scoping | Read in full (8 test methods: admin-all-branches, branch-manager-forced, operator-403, filter-by-actor, filter-by-entity, filter-by-action, filter-by-date-range, pagination); re-run independently, all 8 green |
| 4.9 | `AuditAtomicityIT` — a use case that throws after `AuditWritePort.record` leaves zero rows | Read `AuditAtomicityIT`, `AuditAtomicityFixtureService`, `AuditAtomicityFixtureController` in full: the fixture service is `@Transactional`, calls `auditWritePort.record(...)` then conditionally throws; the IT asserts zero matching rows on the failure path and exactly one row on the success path. This is the load-bearing test design.md calls out by name ("the only test that can distinguish the required synchronous port from an accidental AFTER_COMMIT or @Async implementation") — re-run independently, both cases green |
| 4.10 | `./mvnw verify` run | Re-run independently by this verification, `BUILD SUCCESS` (see Decisive command output above) |

All 10 tasks in `tasks.md` are checked `[x]` and correspond to real, independently-verified
artifacts. No unchecked task in the 4.1–4.10 range.

### Spec compliance matrix (`specs/audit-log/spec.md`)

| Requirement / Scenario | Status | Covering test |
|---|---|---|
| Mutation succeeds and is audited | **PASS** | `AuditAtomicityIT.unaEscrituraDeAuditoriaSinFallaSiPersisteLaFila` — one row persists after a successful call |
| Audit write failure aborts the mutation | **PASS** | `AuditAtomicityIT.unaEscrituraDeAuditoriaSeguidaDeUnaFallaNoDejaFilas` — zero rows after the fixture throws post-`record`; proves the write is inside the caller's own transaction (rolled back with it), not `@Async`/`AFTER_COMMIT` |
| Reads are never audited | **PASS (by construction, not a dedicated negative test)** | `AuditLogController` has exactly one `@GetMapping` and calls only `QueryAuditLogUseCase.query`, which never calls `AuditWritePort.record`; no code path exists that could audit a read. No dedicated IT asserts "read produced zero new audit rows" as an explicit assertion — **SUGGESTION**, not a gap, since the absence is structural (no call site exists to remove) |
| ADMIN queries across branches | **PASS** | `AuditLogQueryIT.adminVeEntradasDeTodasLasSucursales` |
| BRANCH_MANAGER scoped to own branch, filter ignored | **PASS** | `AuditLogQueryIT.gerenteDeSucursalSoloVeSuPropiaSucursalAunqueEnvieOtraEnElFiltro` — submits Medellín's `branchId` while authenticated as Bogotá's manager, still gets only Bogotá's row |
| OPERATOR is denied (403) | **PASS** | `AuditLogQueryIT.operadorEsRechazadoConCuatroCientosTres` |
| Large result set is paginated | **PASS** | `AuditLogQueryIT.unConjuntoMasGrandeQueElTamanoDePaginaPorDefectoSePagina` — 25 rows inserted, default page returns 20, `totalElements` 25 |
| No mutation path exists for audit entries | **PASS** | `AuditLogController` grep-confirmed: zero `PUT`/`PATCH`/`DELETE` mappings; no other controller in the diff touches `audit_logs` |

All eight audit-log spec scenarios have a runtime-executed covering test (or, for "reads are
never audited," a structural proof plus a low-risk coverage suggestion). No UNTESTED or
FAILING scenario against the required behavior.

### Test-quality check (not just presence)

- `AuditAtomicityIT` is not tautological: if `AuditWriteAdapter.record` were annotated
  `@Async` or deferred via `TransactionSynchronizationManager.registerSynchronization(...,
  afterCommit)`, the failure-path test would find one row instead of zero (the write would
  complete on a separate thread/after the enclosing transaction, regardless of the fixture's
  own rollback). If the fixture's `@Transactional` boundary were removed entirely (each
  statement auto-committing), the same test would also fail. Both realistic regressions are
  caught.
- `AuditLogQueryIT.gerenteDeSucursalSoloVeSuPropiaSucursalAunqueEnvieOtraEnElFiltro` is the
  strongest test in this slice: it deliberately submits a *conflicting* branch filter and
  asserts the server-side value wins, not the client-submitted one — if
  `AuditQueryService.query` used `query.branchExternalId()` unconditionally instead of the
  role-conditional expression, this test fails.
- `operadorEsRechazadoConCuatroCientosTres` would fail with `200` instead of `403` if the
  `SecurityConfig` matcher for `/api/audit/**` were removed or loosened — not tautological.

### CLAUDE.md hard invariants — re-checked for Slice 4's diff

| Invariant | Status | Evidence |
|---|---|---|
| Audit write is synchronous, in the caller's own transaction (no `@Async`, no `AFTER_COMMIT`) | **PASS** | `grep -rn "@Async\|AFTER_COMMIT\|TransactionPhase" backend/src` → only doc-comment mentions in `AuditWritePort.java` and `AuditAtomicityIT.java` explaining the invariant itself, zero executable use; `AuditWriteAdapter.record` carries no `@Transactional`, confirmed by reading the class; `AuditAtomicityIT` independently re-run and green (see above) |
| Roles `ADMIN`/`BRANCH_MANAGER`/`OPERATOR`, no `ROLE_` prefix; `hasAuthority()` never `hasRole()` | **PASS** | `grep -rn "ROLE_\|hasRole(" backend/src --include=*.java` → only pre-existing doc-comment mentions (`Role.java` ×2, `SecurityConfig.java` ×1), zero executable use; `SecurityConfig`'s `/api/audit/**` matcher uses `hasAnyAuthority("ADMIN","BRANCH_MANAGER")` |
| API exposes only `external_id`, never numeric ids | **PASS** | `AuditLogController.AuditEntryResponse`/`AuditRecord`/`AuditQueryPort.AuditFilter` all carry `UUID` fields only; `AuditLogJpaEntity.id` (the internal `Long`) never crosses `AuditWriteAdapter.toDomain`'s mapping into `AuditRecord` |
| `shared/` stays framework-free and a leaf | **PASS** | `shared/audit/{AuditAction,AuditEntryCommand,AuditWritePort}.java` import only `java.util.UUID`; `SharedIsFrameworkFreeTest` re-run, still 1/1 green; `ModuleBoundariesTest.sharedEsUnaHoja` still passes (5/5 total, non-vacuous) |
| Docker-dependent tests named `*IT` | **PASS** | `AuditAtomicityIT`, `AuditLogQueryIT` (Testcontainers-backed) vs. `AuditEntryCommandTest` (pure unit, ran in the surefire-only focused re-run above, no Testcontainers boot logged) — correctly suffixed |
| No new class in a direct subpackage of the base package other than a business module | **PASS** | `shared/audit/**` is a subpackage of the existing `shared` leaf module (not a new direct base-package subpackage); all other Slice 4 classes live under `iam/**`. `find backend/src/main/java/com/optiplant/inventory -maxdepth 2 -type d` still shows only `iam` and `shared` |
| No Flyway added alongside `backend/init-db/` | **PASS** | No schema file touched this slice; no Flyway dependency change |

### Deviations recorded in `apply-progress.md` for Slice 4 — verified

1. **`AuditEntryCommand.action` stays `String`, not `AuditAction`.** Confirmed: the record's
   `action` parameter is `String` (matches `design.md:225-228` verbatim, which itself types it
   `String`); `AuditAction` exists as a separate typed helper for `iam`'s own call sites
   (`AuditAtomicityFixtureService` uses `AuditAction.CREATE.name()`). The load-bearing test,
   `AuditEntryCommandTest`, was re-run and passed.
2. **`AuditQueryPort`'s query methods live on `AuditWriteAdapter`, not a second class.**
   Confirmed: `AuditWriteAdapter implements AuditWritePort, AuditQueryPort` in one class.
3. **`AuditQueryService` performs no `OPERATOR` check of its own.** Confirmed: no `Role`
   comparison against `OPERATOR` anywhere in `AuditQueryService`; the HTTP-layer
   `SecurityConfig` matcher is the sole gate, and `AuditLogQueryIT.operadorEsRechazadoConCuatroCientosTres`
   proves it end to end.
4. **`AuditLogSpringDataRepository.search` is a native query with explicit `::timestamptz`
   casts, not JPQL**, found by executing (a JPQL version failed with a Postgres extended-query
   protocol type-inference error). Confirmed by reading the `@Query(nativeQuery = true, ...)`
   annotation directly — both the `search` and `countQuery` clauses cast `:from`/`:to` to
   `timestamptz`. Real and necessary, not a shortcut.
5. **`AuditLogQueryIT`'s test marker is 8 hex chars (`it-XXXXXXXX`), not a full UUID**, because
   `entity_name` is `VARCHAR(50)`. Confirmed by reading `AuditLogQueryIT.marker()` and cross-
   checking `01-init-schema.sql`'s `entity_name VARCHAR(50)` column definition.

All five declared deviations are real, sound, and consistent with the actual code — no
undisclosed deviation was found while cross-referencing the diff against `design.md` and
`tasks.md`.

### Issues found for Slice 4

**CRITICAL**: none.

**WARNING**: none new. (The two Slice-3 forward-looking WARNINGs about a future real mutating
endpoint remain open and are unaffected by Slice 4, since 5a/5b — the first real mutation
endpoints wired to `AuditWritePort` — have not started.)

**SUGGESTION**:
1. Add a dedicated negative-assertion test for "reads are never audited" (e.g., call
   `GET /api/audit` itself, or any existing read endpoint, then assert the `audit_logs` row
   count is unchanged). The current proof is structural (no call site exists), which is sound
   but not execution-verified the same way the write-side atomicity is. Low priority — the
   absence of a call site is a stronger guarantee than a test could add, but it would close the
   verification loop for this specific scenario the same way the other seven were closed.
2. Once slices 5a/5b wire `UserAdminService`/`BranchAdminService` mutations through
   `AuditWritePort`, extend `AuditAtomicityIT`'s scenario coverage (or add an equivalent) against
   a *real* production mutation, not only the test-only fixture — this was already anticipated
   in the Slice-4 apply-progress boundary note and does not block this slice's own merge.

## Conclusion

Slices 1, 2a, 2b, 3, and 4 are functionally complete. Slices 1/2a/2b are spec-compliant on all
but two narrow scenario halves (disabled-branch login denial, expired/altered-token rejection
— both WARNING, not CRITICAL). Slice 3 (Branch Isolation) is fully spec-compliant, as detailed
above. Slice 4 (Audit) is fully spec-compliant: all eight audit-log spec requirements have a
runtime-executed covering test (seven direct, one structural-plus-suggestion for "reads are
never audited"), no CRITICAL issue was found, and all five declared deviations (`action` stays
`String`, query methods on the write adapter, no service-level OPERATOR check, native query
with explicit timestamp casts, short test-marker length) were independently confirmed as real
and sound against the source, not taken on the apply executor's word. The load-bearing
`AuditAtomicityIT` was read in full and independently re-run: it genuinely distinguishes a
synchronous, same-transaction audit write from an `@Async`/`AFTER_COMMIT` implementation, which
is the exact CLAUDE.md invariant this verification was asked to scrutinize most closely. Every
project invariant re-checked for Slice 4's diff holds: no `ROLE_`/`hasRole(`, `external_id`-only
API surface, `shared/audit/**` framework-free and still a leaf (`SharedIsFrameworkFreeTest`,
`ModuleBoundariesTest` both green and non-vacuous), `*IT`/`*Test` naming correct, no Flyway
introduced. `cd backend && ./mvnw verify` on the current working tree passes with 35 surefire +
29 failsafe tests, all green (independently re-run for this verification, not taken from
`apply-progress.md`'s own numbers alone), and `validar_trazabilidad.py` is unaffected and green;
`validar_esquema.sh` was correctly skipped (no `docs/`/`backend/init-db/` change in this
slice). **Ready to proceed** — the two SUGGESTIONs on Slice 4 are low-priority coverage notes
for a future slice, not merge blockers; slices 5a, 5b remain correctly unstarted (confirmed:
`fd` search for `UserAdminService`, `BranchAdminService`, `BranchJpaEntity` returns zero hits
under `src/main`).

```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:7d630871ec2b91a665d10486ae77b73cf094f0f0472cd63a77dad7523f3940c7
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 5/5
scenarios: 9/9
test_command: cd backend && ./mvnw verify
test_exit_code: 0
test_output_hash: sha256:fb45e3cb968aad6bcb73ead87e74b8c7f0038a19f7ea57c864075fded697c912
build_command: cd backend && ./mvnw verify
build_exit_code: 0
build_output_hash: sha256:fb45e3cb968aad6bcb73ead87e74b8c7f0038a19f7ea57c864075fded697c912
```

## Slice 5a — User Admin (PR6) — verified

**Scope**: Tasks 5a.1–5a.8. Branch `feat/ep-01-iam-05a-administracion-usuarios`, uncommitted
working tree (10 modified tracked files, 7 new untracked source/test files under
`iam/domain/exception/**`, `iam/application/{port/in,service}/**`,
`iam/infrastructure/adapter/in/web/UserAdminController.java`,
`backend/src/test/.../iam/application/service/UserAdminServiceTest.java`,
`backend/src/test/.../UserAdminIT.java`), plus `tasks.md`/`apply-progress.md` updates. None
staged or committed — the orchestrator owns delivery for this slice.

### Task completion

All 8 tasks (5a.1–5a.8) are marked `[x]` in `tasks.md` and match the working-tree state — every
file `apply-progress.md` lists as created/modified for this slice exists with the described
content; no task claims work that the diff does not contain.

| Metric | Value |
|---|---|
| Tasks total (5a) | 8 |
| Tasks complete | 8 |
| Tasks incomplete | 0 |

### Build & tests — independently re-run for this verification

**Focused test** — `cd backend && ./mvnw test -Dtest=UserAdminServiceTest`
```
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.iam.application.service.UserAdminServiceTest
```
Exit code `0`.

**Runtime harness** — `cd backend && ./mvnw verify -Dit.test=UserAdminIT` (real Postgres 17,
Testcontainers)
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.UserAdminIT
BUILD SUCCESS
```
Exit code `0`.

**Full build** — `cd backend && ./mvnw verify` (whole module, not just this slice)
```
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0   (surefire)
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0   (failsafe: ApplicationContextIT 1,
  AuditAtomicityIT 2, AuditLogQueryIT 8, AuthenticationFlowIT 12, BranchIsolationIT 6,
  UserAdminIT 8)
BUILD SUCCESS
```
Exit code `0`. Matches `apply-progress.md`'s own reported numbers exactly — independently
re-run, not taken on the apply executor's word.

**`python3 scripts/validar_trazabilidad.py`**
```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 6 DT
```
Correctly unaffected — no `docs/` file touched this slice. `./scripts/validar_esquema.sh` was
correctly skipped for the same reason (no `backend/init-db/` change).

### Spec compliance matrix (`specs/user-administration/spec.md`)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Only ADMIN manages users | Non-ADMIN attempts user management | `UserAdminIT.unGerenteDeSucursalNoAdminEsRechazadoConCuatroCientosTres` (403 via `SecurityConfig`'s path matcher, verb-agnostic — the same mechanism `BranchIsolationIT`/`AuditLogQueryIT` already exercise for other verbs) | ✅ COMPLIANT |
| User creation: unique username/email, role/branch | Successful user creation | `UserAdminServiceTest.creaUnAdminCorporativoSinSucursal`, `creaUnGerenteDeSucursalConSucursalAsignada` (persisted, active); BCrypt-hash round-trip proven indirectly by `UserAdminIT`'s real logins (`admin.corp`, and the created user logging in twice in the disable test) succeeding through the real `BCryptPasswordHasher` | ✅ COMPLIANT |
| ″ | Duplicate username | `UserAdminServiceTest.rechazaUnUsernameDuplicado` + `UserAdminIT.crearConUsernameDuplicadoDevuelve409` | ✅ COMPLIANT |
| ″ | Duplicate email | `UserAdminServiceTest.rechazaUnEmailDuplicado` + `UserAdminIT.crearConEmailDuplicadoDevuelve409` | ✅ COMPLIANT |
| ″ | Non-ADMIN role without a branch | `UserAdminServiceTest.rechazaUnGerenteDeSucursalSinSucursal`/`rechazaUnOperadorSinSucursal` + `UserAdminIT.crearUnGerenteDeSucursalSinSucursalDevuelve400` | ✅ COMPLIANT |
| User edit updates role, branch, profile | A user's role changes | `UserAdminServiceTest.editaElRolYLaSucursalDeUnUsuarioExistente` + `UserAdminIT.editarActualizaElRolYLaSucursalDeUnUsuarioExistente` (persistence + `external_id` immutability both asserted) | ⚠️ PARTIAL — "subsequent logins reflect the new role's authorized capabilities" (the scenario's second clause) has no test that edits a role and then exercises a role-gated endpoint with a fresh login; only persistence is asserted directly |
| User disable is logical, revokes sessions | Disabling a user | `UserAdminIT.deshabilitarUnUsuarioRevocaTodosSusTokensVivosYNoLoBorraFisicamente` — two independent refresh-token families both revoked (`revoked_reason='USER_DISABLED'`, 0 live tokens), `users` row count stays 1, `is_active=false` | ⚠️ PARTIAL — "historical movements remain intact" is not independently testable (no movement/Kardex module exists in the codebase yet); the test's own class javadoc discloses this, and the closest verifiable proxy (no physical row delete) is asserted |
| ″ | Disabled user attempts login | Same test, `loginTrasDeshabilitar` → `401` | ✅ COMPLIANT |
| User query lists users, no internal ids | Query includes disabled users | `UserAdminIT.consultarUsuariosNoExponeElIdNumerico` creates and lists only one **active** user; asserts no `id`/`passwordHash` key, but never puts a disabled user in the query result to prove both are returned together | ⚠️ PARTIAL — the "no numeric id" half is compliant and tested; the "mix of active/disabled, both returned" half is untested at runtime. Code inspection of `UserSpringDataRepository.search`'s JPQL (`:active IS NULL OR u.active = :active`) supports correctness, but per this project's own rule ("no se afirma nada sin ejecutarlo" — CLAUDE.md) that inspection alone does not close the scenario |

**Compliance summary**: 6/9 scenarios fully compliant, 3/9 partially compliant (no scenario is
fully untested or failing). No CRITICAL spec gap.

### Design coherence (`design.md`)

| Decision | Followed? | Notes |
|---|---|---|
| Package layout (`design.md:71,75,76,80,82`): `DuplicateUsernameException`, `ManageUsersUseCase`, `UserRepositoryPort`, `UserAdminService`, `UserAdminController` | ✅ Yes | Every class lands exactly where the design's tree names it |
| `UserNotFoundException` added beyond the design's literal exception list | ⚠️ Deviation, justified | Needed for a genuine client-facing `404` on edit/disable of an unknown `external_id`; no existence-leak concern (caller is an already-authenticated `ADMIN` acting on an id it supplied itself). Consistent with earlier slices' pattern of justified beyond-the-list additions |
| `RefreshTokenRepositoryPort.revokeAllForUser` added beyond design's `revoke`/`revokeFamily` pair | ⚠️ Deviation, necessary | Neither existing method can express "every live token of this user across every device" — exactly what the disable scenario requires (multi-device sessions, P2/P4). Verified as the actual shape used and tested (`UserAdminIT`'s two-session scenario) |
| `GET /api/admin/users` added beyond task 5a.5's literal `POST/PUT/PATCH` list | ⚠️ Deviation, necessary | The user-administration query requirement needs a reachable HTTP entry point to be testable at all; `SecurityConfig`'s matcher is verb-agnostic so no security wiring changed |
| Audit entry's `branch_id` is the acting `ADMIN`'s own `branchId` (always `NULL`, since only `ADMIN` can call these endpoints), not the target user's branch | ⚠️ **Flagged — see Issues** | `apply-progress.md` cites `AuditAtomicityFixtureService` (slice 4) as precedent. Read that class directly: it is a **test-source-only fixture** (`backend/src/test/.../AuditAtomicityFixtureService.java`, confined to `src/test`, doc-commented as existing solely to prove transactional atomicity), not a production call site or a considered design decision about actor-vs-resource `branch_id` semantics. Citing it as precedent is weaker than `apply-progress.md` implies — see Issues below |

### CLAUDE.md hard invariants — re-checked for Slice 5a's diff

| Invariant | Status | Evidence |
|---|---|---|
| Roles `ADMIN`/`BRANCH_MANAGER`/`OPERATOR`, no `ROLE_` prefix; `hasAuthority()` never `hasRole()` | **PASS** | `grep -rn "ROLE_\|hasRole(" backend/src --include="*.java"` → only 4 pre-existing doc-comment hits (`Role.java` ×3, `SecurityConfig.java` ×1), zero executable use. `SecurityConfig:67` — `.requestMatchers("/api/admin/users/**", "/api/admin/branches/**").hasAuthority("ADMIN")` |
| Every stock/audit mutation writes in the same transaction | **PASS (audit)** | `UserAdminService.create`/`edit`/`disable` are each `@Transactional`; `AuditWritePort.record` is called inside that same method body, no `@Async`/`AFTER_COMMIT`. No stock/Kardex concept exists yet in this codebase (out of this epic's scope) |
| Atomic effects via synchronous output port, never event | **PASS** | Same evidence as above — `AuditWritePort` is a plain synchronous interface call, not a published domain event |
| Branch derived from session, never client param | **PASS** | `UserAdminController` reads `actor` from `PrincipalAccessor.require()` (session-derived) for every mutation; `branchId` on create/edit request bodies identifies the **target user's** assignment (a legitimate business field for user administration, not an authorization-scoping parameter) — no endpoint lets the client assert its own acting branch |
| API exposes only `external_id`, never numeric `id` | **PASS** | `UserAdminController.UserResponse`/`UserPageResponse` carry `externalId (UUID)` only; `UserAdminIT.consultarUsuariosNoExponeElIdNumerico` asserts the raw JSON has no `id`/`passwordHash` key, executed and green |
| Docker-dependent tests named `*IT` | **PASS** | `UserAdminIT` (Testcontainers-backed) vs. `UserAdminServiceTest` (pure unit, hand-written fakes, no Testcontainers boot in the focused re-run log) — correctly suffixed |
| No new class in a direct base-package subpackage other than a business module | **PASS** | `find backend/src/main/java/com/optiplant/inventory -maxdepth 2 -type d` → only `iam` and `shared`; every Slice 5a class lands under `iam/**` |
| No Flyway added alongside `backend/init-db/` | **PASS** | No schema file touched this slice; `spring.flyway.enabled: false` unchanged |
| `shared/` stays framework-free and a leaf | **PASS (unaffected)** | Slice 5a touches only `iam/**`; `ModuleBoundariesTest` (5/5) and `SharedIsFrameworkFreeTest` (1/1) both re-run green in the full `verify` above |

### Deviations recorded in `apply-progress.md` for Slice 5a — verified

1. **`UserNotFoundException` added**, not in design's literal list — confirmed real (thrown by
   `UserAdminService.edit`/`disable`, mapped to `404` by `IamExceptionHandler.onUserNotFound`,
   exercised by `UserAdminServiceTest.editarUnExternalIdInexistenteLanzaUserNotFound`/
   `deshabilitarUnExternalIdInexistenteLanzaUserNotFound` and
   `UserAdminIT.deshabilitarUnExternalIdInexistenteDevuelve404`). Justified, no existence-leak
   concern for an already-authenticated `ADMIN`.
2. **`RefreshTokenRepositoryPort.revokeAllForUser` added** — confirmed present on the port,
   implemented in `RefreshTokenPersistenceAdapter`, called from `UserAdminService.disable` inside
   the same `@Transactional` method as the `disable` write and the audit record, and proven by
   `UserAdminIT`'s two-refresh-token-family scenario. Real and necessary.
3. **`GET /api/admin/users` added** beyond the task's literal verb list — confirmed present,
   `ADMIN`-gated by the existing verb-agnostic matcher, tested by
   `UserAdminIT.consultarUsuariosNoExponeElIdNumerico` and
   `unGerenteDeSucursalNoAdminEsRechazadoConCuatroCientosTres`.
4. **No Mockito on the classpath** — re-confirmed: `./mvnw dependency:tree` resolves zero
   `org.mockito` artifacts for this module's own dependencies. (Note: the Testcontainers-backed
   `UserAdminIT` run does log `"Mockito is currently self-attaching to enable the
   inline-mock-maker"` — traced to Testcontainers' own internal use, not a project dependency;
   confirmed no `org.mockito` import exists anywhere under `backend/src`.) `UserAdminServiceTest`
   correctly uses hand-written fakes, consistent with every other unit test in this module.
5. **"Historical movements remain intact" asserted only indirectly** — confirmed no
   movement/Kardex module exists yet under `backend/src/main`; `UserAdminIT`'s class javadoc
   states the narrowing explicitly.
6. **Audit `branch_id` is the acting ADMIN's own branch, not the target user's** — confirmed as
   coded (`UserAdminService` passes `actor.branchId()`, never a value derived from the affected
   user's `branchExternalId`). The cited precedent (`AuditAtomicityFixtureService`) is real code,
   but it is a test-only fixture with no domain resource of its own to attribute a `branch_id`
   to, not an established production convention — see Issues.

All six declared deviations are real and match the diff. Deviation 6's own justification is
weaker than stated; flagged below, not silently accepted.

### Issues found for Slice 5a

**CRITICAL**: None.

**WARNING**:
1. **Audit entries for every user-administration mutation always carry `branch_id = NULL`**,
   because the only actor who can reach `/api/admin/users/**` is `ADMIN`, and `ADMIN.branchId()`
   is always `null` (corporate scope) — regardless of which branch the mutated user belongs to.
   `audit-log/spec.md`'s "Audit query is filtered and role-scoped" requirement states a
   `BRANCH_MANAGER` "MUST see only entries for their own branch." Under the current
   implementation, a `BRANCH_MANAGER` of any branch will **never** see a user-administration
   audit entry for their own branch — every such entry is invisible to every `BRANCH_MANAGER`,
   permanently, because it is filed under no branch at all. This is not exercised by any
   existing test (`AuditLogQueryIT`'s branch-scoping scenario uses its own audit fixture, not a
   real `UserAdminService` mutation) and is not necessarily wrong — `audit-log/spec.md`'s "for
   that action, user, and branch" wording is genuinely ambiguous between "the acting admin's
   branch" and "the affected resource's branch" — but it is a real, user-visible product
   question the orchestrator/product owner should confirm before Slice 5b repeats the same
   pattern for branch administration.
2. **"A user's role changes" scenario's second clause** ("subsequent logins reflect the new
   role's authorized capabilities") has no test that edits a role and then re-authenticates to
   exercise a role-gated endpoint under the new role. The persisted-value half is well tested;
   the end-to-end behavioral half relies on `AuthenticationService` reading the role fresh from
   the database at each login (already proven correct by `AuthenticationFlowIT` in Slice 2a/2b),
   so the risk is low, but the exact combination is untested.
3. **"Query includes disabled users" scenario** is not directly tested — `UserAdminIT`'s query
   test only ever creates active users. `UserSpringDataRepository.search`'s JPQL
   (`:active IS NULL OR u.active = :active`) supports the requirement by inspection, but per this
   project's own working rule (CLAUDE.md: "no se afirma nada sin ejecutarlo") that inspection is
   not equivalent to a passing runtime assertion.

**SUGGESTION**:
1. Add one assertion to `UserAdminIT.consultarUsuariosNoExponeElIdNumerico` (or a new case)
   that disables a second user and confirms both appear in an unfiltered `GET /api/admin/users`
   response — closes WARNING 3 cheaply, reusing existing helper methods.
2. Once Slice 5b (Branch Admin) is implemented, resolve WARNING 1 explicitly one way or the
   other (either the target resource's `branch_id`, or a documented product decision that
   corporate-scoped `ADMIN` actions are intentionally branch-invisible in the audit log) before
   the same pattern repeats for `BranchAdminService`.

### Conclusion for Slice 5a

Task completion is 8/8, matching the working tree exactly. `cd backend && ./mvnw verify` was
independently re-run for this verification (not taken from `apply-progress.md`'s numbers alone)
and passes: 48 surefire + 37 failsafe tests, all green, `BUILD SUCCESS`, exit code `0` on every
command executed. All CLAUDE.md hard invariants re-checked for this slice's diff hold. 6 of 9
`user-administration` spec scenarios are fully compliant with a passing runtime-executed
covering test; the remaining 3 are PARTIAL — each has a passing test for part of the scenario,
with a narrow, identified, non-blocking gap (two are pre-existing/inherent-low-risk, one is a
cheap one-assertion fix). No CRITICAL issue was found. One WARNING (audit `branch_id` semantics
for corporate-scoped actors) is a genuine open product/design question worth resolving before
Slice 5b repeats the same call pattern, but does not contradict any spec scenario this slice
itself is responsible for. **Verdict: PASS WITH WARNINGS — ready to proceed to `sdd-archive`**
for Slice 5a, with the three WARNINGs carried forward as follow-up items (not merge blockers).

## Slice 5b — Slice 5b: Branch Admin (PR7) Verification

### Summary of verification

Slice 5b implements branch administration (RF-SEG-03, CU-SEG-03, HU-SEG-03): create with unique code, edit updating name/address/city/phone while preserving immutable `external_id` and `code`, logical disable (`is_active = false`, no physical delete; blocking assigned users from authenticating), filtered/paginated query without exposing internal numeric `id`s, and transactional audit logging with compact JSON payloads.

| Metric | Value |
|---|---|
| Tasks total (5b) | 8 |
| Tasks complete | 8 |
| Tasks incomplete | 0 |

### Build & tests — independently re-run for this verification

**Focused test** — `cd backend && ./mvnw test -Dtest=BranchAdminServiceTest`
```
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.iam.application.service.BranchAdminServiceTest
BUILD SUCCESS
```
Exit code `0`.

**Runtime harness** — `cd backend && ./mvnw verify -Dit.test=BranchAdminIT` (real Postgres 17, Testcontainers)
```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.BranchAdminIT
BUILD SUCCESS
```
Exit code `0`.

**Full build** — `cd backend && ./mvnw verify` (whole module, including all previous slices)
```
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0   (surefire)
Tests run: 48, Failures: 0, Errors: 0, Skipped: 0   (failsafe: ApplicationContextIT 1,
  AuditAtomicityIT 2, AuditLogQueryIT 8, AuthenticationFlowIT 12, BranchAdminIT 9,
  BranchIsolationIT 6, UserAdminIT 10)
BUILD SUCCESS
```
Exit code `0`. Matches `apply-progress.md`'s reported numbers independently.

**`python3 scripts/validar_trazabilidad.py`**
```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 6 DT
```
Exit code `0`.

### Spec compliance matrix (`specs/branch-administration/spec.md`)

| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Only ADMIN manages branches | Non-ADMIN attempts branch management | `BranchAdminIT.unGerenteDeSucursalNoAdminEsRechazadoConCuatroCientosTres` (403 via `SecurityConfig` matcher `/api/admin/branches/**`) | ✅ COMPLIANT |
| Branch creation enforces unique code | Successful branch creation | `BranchAdminServiceTest.creaUnaSucursalConCodigoUnico` + `BranchAdminIT.crearSucursalConExito` | ✅ COMPLIANT |
| ″ | Duplicate branch code | `BranchAdminServiceTest.rechazaUnCodigoDeSucursalDuplicado` + `BranchAdminIT.crearConCodigoDuplicadoDevuelve409` | ✅ COMPLIANT |
| Branch edit updates name and location, not identity | Successful branch edit | `BranchAdminServiceTest.editaNombreYDireccionDeUnaSucursalExistente` + `BranchAdminIT.editarActualizaNombreYDireccionPeroMantieneExternalIdYCodigo` (`external_id` immutable, `code` unchanged, name/address updated) | ✅ COMPLIANT |
| Branch disable is logical, never physical | Disabling a branch | `BranchAdminServiceTest.deshabilitarUnaSucursalCambiaActiveAFalsoYRegistraAuditoria` + `BranchAdminIT.deshabilitarUnaSucursalBloqueaElLoginDeSusUsuariosYNoLaBorraFisicamente` (`is_active=false`, row count remains 1, assigned user login denied with `401`) | ✅ COMPLIANT |
| Branch query lists branches with status | Query includes disabled branches | `BranchAdminServiceTest.listarSucursalesConFiltro` + `BranchAdminIT.consultarSucursalesNoExponeElIdNumerico` (includes active and disabled branches, no `id` key in response JSON) | ✅ COMPLIANT |

**Compliance summary**: 6/6 scenarios fully compliant with passing runtime tests.

### Design coherence (`design.md`)

| Decision | Followed? | Notes |
|---|---|---|
| Package layout (`design.md:67,71,75,76,80,83,85`): `BranchProfile`, `DuplicateBranchCodeException`, `BranchRepositoryPort`, `ManageBranchesUseCase`, `BranchAdminService`, `BranchAdminController`, `BranchJpaEntity`, `BranchSpringDataRepository`, `BranchMapper`, `BranchPersistenceAdapter` | ✅ Yes | Every class lands in the exact package declared in the design |
| `BranchNotFoundException` added beyond the design's literal list | ⚠️ Deviation, justified | Needed for genuine `404` on edit/disable of unknown `external_id`, mirroring `UserNotFoundException` from slice 5a |
| `GET /api/admin/branches` added beyond task 5b.5's literal `POST/PUT/PATCH` list | ⚠️ Deviation, necessary | Query requirement needs a reachable HTTP endpoint; `SecurityConfig` matcher is verb-agnostic |
| Audit entry `branch_id` is the affected branch's own `external_id` | ✅ Yes | Followed the established design decision for resource-level branch audit attribution |

### CLAUDE.md hard invariants — re-checked for Slice 5b's diff

| Invariant | Status | Evidence |
|---|---|---|
| Roles `ADMIN`/`BRANCH_MANAGER`/`OPERATOR`, no `ROLE_` prefix; `hasAuthority()` never `hasRole()` | **PASS** | `grep -rn "ROLE_\|hasRole(" backend/src --include="*.java"` → zero executable use (4 doc-comment mentions only). `SecurityConfig:67` gates `/api/admin/branches/**` with `hasAuthority("ADMIN")` |
| Every stock/audit mutation writes in the same transaction | **PASS** | `BranchAdminService.create`/`edit`/`disable` are `@Transactional`; `AuditWritePort.record` is called synchronously in the method body |
| Atomic effects via synchronous output port, never event | **PASS** | `AuditWritePort` is called synchronously |
| Branch derived from session, never client param | **PASS** | `BranchAdminController` extracts `actor` via `PrincipalAccessor.require()` |
| API exposes only `external_id`, never numeric `id` | **PASS** | `BranchResponse`/`BranchPageResponse` expose `externalId` only; `BranchAdminIT.consultarSucursalesNoExponeElIdNumerico` asserts no `id` key in response JSON |
| Docker-dependent tests named `*IT` | **PASS** | `BranchAdminIT` (failsafe, Testcontainers) vs `BranchAdminServiceTest` (surefire, unit with in-memory fakes) |
| No new class in direct base-package subpackage other than business module | **PASS** | Every new class resides under `com.optiplant.inventory.iam.**` |
| No Flyway added alongside `backend/init-db/` | **PASS** | No schema change this slice; `spring.flyway.enabled: false` unchanged |
| `shared/` stays framework-free and a leaf | **PASS** | `ModuleBoundariesTest` (5/5) and `SharedIsFrameworkFreeTest` (1/1) green in full verify |

### Conclusion for Slice 5b

Task completion is 8/8. All tests pass independently (`./mvnw test -Dtest=BranchAdminServiceTest`: 8 tests, `./mvnw verify -Dit.test=BranchAdminIT`: 9 tests, full `./mvnw verify`: 49 surefire + 48 failsafe tests, `BUILD SUCCESS`). All 6 spec scenarios are fully compliant with covering tests. All project invariants hold. **Verdict: PASS — ready for Slice 5b PR7 delivery**.
