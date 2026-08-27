# Verification Report: `add-iam-module` — Slices 1, 2a, 2b

**Scope**: Tasks 1.1–1.19 (Foundations), 2a.1–2a.8 (Login), 2b.1–2b.12 (Refresh + Logout).
Slices 3, 4, 5a, 5b are intentionally out of scope (not started) and are reported as
not-started, not as failures.

**Commit verified**: `2240b17` on `feat/ep-01-iam-02b-refresh-logout` (tip of the chained-PR
stack `feat/ep-01-iam-01-foundations → 02a-login → 02b-refresh-logout`, all branching from
tracker `feat/ep-01-iam`). Working tree clean at verification time (only a locally-generated,
untracked `.codegraph/` index directory, created by this verification pass itself).

## Verdict per slice

| Slice | Verdict |
|---|---|
| 1 — Foundations | **PASS** |
| 2a — Login | **PASS** |
| 2b — Refresh + Logout | **PASS WITH WARNINGS** (2 minor coverage/design-deviation warnings, no CRITICAL) |

**Overall: the three implemented slices are ready to merge.** No CRITICAL issue found.

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
- Tasks 3.1 onward: all unchecked in `tasks.md`, and confirmed **not present** in source
  (`fd` search for `BranchAccessPolicy`, `CrossBranchMutationException`, `AuditWritePort`,
  `UserAdminService`, `BranchAdminService`, `BranchJpaEntity`, `AuditLogJpaEntity` — zero
  hits). Consistent — reported as not-started, not a failure.

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

## Conclusion

Slices 1, 2a, and 2b are functionally complete, spec-compliant on all but two narrow scenario
halves (disabled-branch login denial, expired/altered-token rejection — both WARNING, not
CRITICAL, and both backed by correct-looking production code even though untested by
execution), and every recorded deviation in `apply-progress.md` was independently verified as
real and justified. The one orchestrator-review fix subsection was independently re-derived
and confirmed genuine. All three project validators (`./mvnw clean verify`,
`validar_trazabilidad.py`, `validar_esquema.sh`) pass. **Ready to merge** as chained PRs
PR1→PR2→PR3 against the `feat/ep-01-iam` tracker branch; slices 3-5 remain correctly
unstarted.
