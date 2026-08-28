# Proposal: `add-iam-module`

- **Driver**: EP-01 — Seguridad y Control de Acceso Multi-Sucursal (3 stories, 13 pts, all `Must`)
- **Modules affected** (arch. §2.4): `iam` (new), `shared` (extended). No other module changes.
- **Delivery**: `auto-chain`, 5 slices, 400 changed lines/slice.

## Intent

The backend has a schema, a boot skeleton and a deny-by-default filter chain, but no identity. `SecurityConfig.java:31-37` authenticates nothing and declares no role rule; `JwtProperties.java:9-11` documents itself as a stopgap awaiting this module. Until `iam` exists, no branch context can be derived, so RN-14 and RNF-SEC-03 are unenforceable and **no other module can be built** — every one of the nine needs to know who is calling and from which branch.

`iam` is the first business module. Its choices (principal shape, audit port, boundary bridge) become the template the other nine copy, which is why they are settled here rather than discovered during implementation.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| 1 | **Access token + persisted, rotating refresh token with revocation** (user-resolved) | RF-SEG-01 (`especificacion_requerimientos.md:85`) demands logout and inactive-session expiry; pure stateless JWT (`decisiones_arquitectura_tecnica.md:131,134`) cannot revoke. Only the *refresh* path touches the DB — access-token validation stays stateless, preserving the multi-instance/no-affinity property that sustains RNF-ESC-03. Logout revokes the refresh token immediately; the access token dies within its short TTL. An access-token denylist was rejected: a DB/cache hit per request forfeits exactly that property. |
| 2 | **`spring-boot-starter-security-oauth2-resource-server` + its `-test` sibling** | Both are managed at 4.1.1 by the platform BOM (`spring-boot-dependencies-4.1.1.pom:2748,2753`); it pulls `spring-security-oauth2-jose` / `-resource-server` at 7.1.1 via the imported `spring-security-bom` (`:3576-3580`, `spring-security-bom-7.1.1.pom:133-139`). No hand-pinned version can drift, and the `-test` pairing matches the pom's existing convention (`pom.xml:36-102`). **jjwt is absent from the BOM** (verified), so it would need a pinned version *and* a hand-written bearer filter. Spring Security owning the filter means less security-critical code written by hand. |
| 3 | **Principal propagation: a framework-free record in `shared`**, populated by an `iam` infrastructure adapter into Spring Security's `Authentication`, read by other modules through a `shared` accessor | `ningunModuloEntraAlInteriorDeOtro` (`ModuleBoundariesTest.java:66-77`) forbids importing `iam` types; `shared` is the only legal bridge and must stay a leaf (`:89-97`) — a record depending on nothing satisfies that. Letting each module parse claims off `Authentication` itself would scatter claim-string handling across nine modules and leave RN-14 with no single enforcement point to test. |
| 4 | **Audit writes go through a synchronous output port declared in `shared`**, implemented by an `iam` adapter inside the caller's transaction | CLAUDE.md: atomic effects use a synchronous port, never an event. Arch. §3.6 puts alerts and analytics in the `AFTER_COMMIT` bucket — not audit. RNF-SEC-08 requires 5-year retention; an entry that can be lost without reverting its operation is not an audit log. |
| 5 | **Minimal in-memory rate limiter on the login endpoint** (user-resolved); restricted CORS included | RNF-SEC-06 is an explicit requirement, and the brute-force vector it closes is the cheapest one to close. A per-identifier counter with a fixed window costs little and keeps RNF-SEC-06 satisfied rather than merely tracked. It is deliberately **not** a distributed limiter: an in-memory window is per-instance, which is an accepted limitation recorded in the design, not a hidden one. No `DT-07` is created, so `docs/deuda_tecnica.md` stays unchanged. |

## Product decisions (user-resolved)

| # | Decision | Consequence |
|---|---|---|
| P1 | **Access token TTL: 15 minutes. Refresh inactivity window: 8 hours** (one warehouse shift) | Bounds the window in which a revoked or disabled user still holds a valid access token to 15 minutes. `JwtProperties` carries both values; they are configuration, not constants in code. |
| P2 | **Disabling a user revokes the refresh token immediately; the live access token dies on its own expiry** (within P1's 15 minutes) | Access-token validation stays stateless, preserving the multi-instance deployment without session affinity that sustains RNF-ESC-03. Rejected alternative: checking user state per request, which forfeits exactly that property. |
| P3 | **The audit log records mutations only**, not reads | Keeps the volume compatible with RNF-SEC-08's 5-year retention. Cross-branch read traceability is explicitly not captured; if RN-08 auditing is wanted later, it is an additive change to the same port. |
| P4 | **Multi-device sessions are allowed**: one user may hold several concurrent refresh tokens, and logout ends only the device presenting the token | `refresh_tokens` therefore has no uniqueness constraint on `user_id`; revocation is per token, and the disable path of P2 revokes every token of that user. |

## Consequences of decision 1 (verified costs)

| Artifact | Change | Evidence |
|---|---|---|
| `backend/init-db/01-init-schema.sql` | New `refresh_tokens` table + indexes, in the IAM section (`:13-46`). **Direct edit — no Flyway** (DT-01; `application.yml` keeps `spring.flyway.enabled: false`). Must carry `external_id UUID`. | `validar_esquema.sh:123-124` fails any public table lacking `external_id` |
| `scripts/validar_esquema.sh` | **`:78` must change `19` → `20`.** The 19 `CREATE TABLE`s counted in the schema make this assertion break the moment a table is added. Plus new invariants: token hash never null, revoked/expired token rejected, `user_id` FK integrity. | `validar_esquema.sh:78` |
| `docs/diagrama_er.md` | New entity in `package "IAM & Organización"` (`:288-317`) + `users ||--o{ refresh_tokens` relation, kept consistent with the SQL | `:289-317,598` |
| `backend/init-db/02-seed-data.sql` | **No change.** Refresh tokens are runtime artifacts; seeding them is meaningless. No new hex prefix consumed (`:7-11`). | — |
| `docs/casos_de_uso.md` | **No change.** Refresh/logout implement RF-SEG-01 as already written; no new RF/RNF/RN identifier is introduced, and `RF-SEG-01 → CU-SEG-01` already exists (`:561`). | `validar_trazabilidad.py:64-69` |

Raw refresh tokens are never stored — only a hash, mirroring `users.password_hash`. Inactivity expiry uses `last_used_at` plus a window, so a refresh presented after the idle window is rejected even while `expires_at` is still future.

## Scope

### In Scope
- `iam` module in the canonical hexagonal layout (arch. §5): authenticate, refresh, logout; user and branch admin CRUD; audit-log query.
- `JwtProperties` moved from the base package into `iam/infrastructure/config`, gaining access/refresh TTLs (today it holds only `secret`, `JwtProperties.java:19-27`).
- `SecurityConfig` gains bearer-token wiring, restricted CORS and authority rules using **`hasAuthority()`** with `ADMIN` / `BRANCH_MANAGER` / `OPERATOR` — no `ROLE_` prefix.
- `shared`: principal record, principal accessor, audit output port.
- Branch isolation: mutations only on the session's own branch, cross-branch reads permitted (RN-08, RN-14, RNF-SEC-03).
- `refresh_tokens` table + the schema-validator and ER-diagram updates above.
- Minimal in-memory rate limiter on the login endpoint (RNF-SEC-06).

### Out of Scope
- **DT-01** (Flyway migration of `init-db/`) and **DT-02** (seed users with a known password) — untouched, still accepted debt.
- Distributed / gateway-level rate limiting — the shipped limiter is per-instance by design.
- TLS termination (RNF-SEC-04) — deployment concern.
- Password reset/change flows, MFA, account lockout — no RF requires them.
- The other nine business modules; the frontend.
- Reintroducing Spring Modulith.

## Capabilities

### New Capabilities
- `authentication`: login, token issuance, refresh rotation, logout, inactivity expiry, login rate limiting (CU-SEG-01, RF-SEG-01, RNF-SEC-06).
- `branch-isolation`: branch context derived from the session; mutation confined to own branch, cross-branch read-only (RN-08, RN-14, RNF-SEC-01/03).
- `user-administration`: user and role CRUD, logical disable only (CU-SEG-02, RF-SEG-02, HU-SEG-03).
- `branch-administration`: branch CRUD with unique code (CU-SEG-03, RF-SEG-03).
- `audit-log`: synchronous write port plus filtered query (CU-SEG-04, RF-SEG-04, RNF-SEC-08).

### Modified Capabilities
None — `openspec/specs/` is empty.

## Slice plan

| # | Boundary | Est. lines | Verification |
|---|---|---|---|
| 1 | **Foundations**: `refresh_tokens` schema, `validar_esquema.sh` count + invariants, ER diagram, 2 pom deps, `shared` principal record + accessor, `JwtProperties` move (now carrying both TTLs of P1) | ~290 | all three validators |
| 2 | **Authentication**: iam domain + ports, login / refresh-rotate / logout use cases, JPA and web adapters, `SecurityConfig` bearer wiring, CORS, BCrypt, login rate limiter | ~450 | `AuthenticationFlowIT`, `LoginRateLimitTest` |
| 3 | **Branch isolation**: `hasAuthority()` rules, branch-context enforcement, 403 on cross-branch mutation | ~250 | `BranchIsolationIT` |
| 4 | **Audit**: synchronous `shared` write port, iam persistence adapter, filtered query endpoint | ~350 | `AuditLogQueryIT` |
| 5 | **Admin CRUD**: user + branch CRUD, ADMIN-gated, audited through slice 4's port | ~400 | `UserAdminIT`, `BranchAdminIT` |

Chosen over the exploration's 3-slice shape (`explore.md:149`) for two reasons: slices 2 and 5 there each exceeded 400 lines, and the audit **write** port must land before the admin CRUD that must produce audit entries — otherwise slice 5 is written twice. Slices 2 and 5 sit at the budget ceiling; under `auto-chain` they may split (2a login / 2b refresh+logout; 5a users / 5b branches) without a new approval.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Resource-server class names assumed from memory (Boot 4 ≠ Boot 3, per CLAUDE.md) | High | The starter JAR is **not yet in `~/.m2`** — only `spring-security-{core,config,web,crypto,test}:7.1.1` are. Coordinates above are BOM-verified; **exact type names MUST be confirmed against the resolved JAR after slice 1 adds the dependency**, never written from memory. |
| Forgetting `validar_esquema.sh:78` → the whole suite fails on an unrelated-looking assertion | High | Made an explicit slice-1 deliverable |
| Principal shape reworked later, invalidating nine modules' adapters | Medium | Settled in slice 1, before any consumer exists |
| OAuth2-named starter used for a first-party token confuses future readers | Medium | Record the rationale in the design doc and a `SecurityConfig` comment |
| ArchUnit fires as soon as `com.optiplant.inventory.iam` appears (`allowEmptyShould` no longer masks it) | Medium | Expected, not a defect — run `./mvnw verify` from the first slice |

## Rollback Plan

Per slice, `git revert` of the slice commit. Slice 1 is the only one with a persistent footprint: reverting the SQL requires `docker compose down -v` before the next start, since an already-initialised volume never re-runs `init-db/`. Slices 2-5 are additive code; reverting restores the current deny-by-default chain. No data migration, so no backfill to undo.

## Dependencies

- Two new Maven dependencies (first download requires network).
- Docker for `validar_esquema.sh` and every `*IT`.
- `JWT_SECRET` propagated without `=` in `compose.yml` (existing behaviour, must not regress).

## Success Criteria

- [x] `python3 scripts/validar_trazabilidad.py` passes. — 42 RF · 34 RNF · 17 RN · 37 CU · 6 DT, íntegro.
- [x] `./scripts/validar_esquema.sh` passes with 20 tables and the new refresh-token invariants. — 25/25 checks, 20 tables.
- [x] `cd backend && ./mvnw verify` passes; `ModuleBoundariesTest` green with `iam` populated. — 56 surefire + 48 failsafe, 0 failures; `ModuleBoundariesTest` 5/5 non-vacuous.
- [x] Login returns an access + refresh pair; refresh rotates and revokes its predecessor; logout makes the refresh token unusable; an idle session past the window is rejected (RF-SEG-01). — `AuthenticationFlowIT` (12/12); idle rejection in `RefreshTokenPolicyTest.unaSesionInactivaMasAlláDeLaVentanaDeInactividadEstaExpiradaPorInactividad`.
- [x] Cross-branch mutation returns 403; cross-branch read succeeds (RN-08, RN-14). — `BranchIsolationIT` (6/6).
- [x] No endpoint accepts a branch identifier from the client; no response exposes a numeric `id`. — `BranchIsolationIT.ningunEndpointAceptaUnBranchIdSuministradoPorElCliente`; grep of `*Response` DTOs found no numeric `id` field.
- [x] No `ROLE_` prefix anywhere in the codebase. — grep of `backend/src` found `ROLE_`/`hasRole(` only in doc comments explaining the invariant, zero executable use.
- [x] Repeated failed logins from the same identifier are throttled (RNF-SEC-06). — `LoginRateLimitTest` (5/5).
- [x] Disabling a user revokes every one of that user's refresh tokens (P2, P4). — `UserAdminIT` (10/10, includes disable-revokes-every-live-token scenario).
