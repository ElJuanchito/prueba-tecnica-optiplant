# Verification Report: `add-iam-module` — Slices 1, 2a, 2b, 3

**Scope**: Tasks 1.1–1.19 (Foundations), 2a.1–2a.8 (Login), 2b.1–2b.12 (Refresh + Logout), 3.1–3.7 (Branch Isolation).
Slices 4, 5a, 5b are intentionally out of scope (not started) and are reported as
not-started, not as failures.

**Commit verified**: `2240b17` (Slices 1/2a/2b, merged into tracker `feat/ep-01-iam`) plus
the uncommitted working tree on `feat/ep-01-iam-03-aislamiento-sucursal` for Slice 3 — `git
status` shows 7 new/modified source files (`CrossBranchMutationException.java`,
`BranchAccessPolicy.java`, `IamExceptionHandler.java`, `SecurityConfig.java`,
`BranchIsolationIT.java`, `BranchAccessPolicyTest.java`, `BranchIsolationFixtureController.java`)
plus `apply-progress.md`/`tasks.md`, none staged or committed. The orchestrator owns delivery
(commit/PR) for Slice 3; this report verifies the working tree bytes as they stand.

## Verdict per slice

| Slice | Verdict |
|---|---|
| 1 — Foundations | **PASS** |
| 2a — Login | **PASS** |
| 2b — Refresh + Logout | **PASS WITH WARNINGS** (2 minor coverage/design-deviation warnings, no CRITICAL) |
| 3 — Branch Isolation | **PASS** |

**Overall: all four implemented slices are ready to merge.** No CRITICAL issue found in any
slice, including Slice 3.

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

## Conclusion

Slices 1, 2a, 2b, and 3 are functionally complete. Slices 1/2a/2b are spec-compliant on all but
two narrow scenario halves (disabled-branch login denial, expired/altered-token rejection —
both WARNING, not CRITICAL). Slice 3 (Branch Isolation) is fully spec-compliant: all four
branch-isolation requirements have a runtime-executed covering test for their in-scope half, no
CRITICAL issue was found, and all three declared deviations (opaque-resource fixture instead of
a branch-id parameter, `BranchAccessPolicy` as a `new`-able non-bean matching
`RefreshTokenPolicy`'s pattern, the exception message omitting the target branch id) were
independently confirmed as real and sound against the source, not taken on the apply
executor's word. Every project invariant re-checked (no `ROLE_`/`hasRole(`, session-derived
branch, `external_id`-only surface, `shared` as a framework-free leaf, `*IT`/`*Test` naming, no
new base-package subpackage) holds for Slice 3's diff. `cd backend && ./mvnw verify` on the
current working tree passes with 32 surefire + 19 failsafe tests, all green, and
`validar_trazabilidad.py` is unaffected and green; `validar_esquema.sh` was correctly skipped
(no `docs/`/`backend/init-db/` change in this slice). **Ready to proceed** — the two open
WARNINGs on Slice 3 are forward-looking scope notes for Slices 4/5, not merge blockers; slices
4, 5a, 5b remain correctly unstarted.
