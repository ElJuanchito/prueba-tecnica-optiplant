# Exploration: `add-iam-module`

- **Change**: `add-iam-module`
- **Phase**: explore
- **Driver**: EP-01 — Seguridad y Control de Acceso Multi-Sucursal
- **Status**: complete (open questions listed in §8 are genuine design forks, not investigation gaps)

Scope of the investigation: implement the `iam` module, the first business module of the backend.

## 1. Requirements scope (traceability)

**Epic**: EP-01 (`docs/historias_de_usuario.md:36,47`) — 3 user stories, all `Must`, 13 points total.

| ID | Summary | Source |
|---|---|---|
| HU-SEG-01 | Authenticate and receive a branch-scoped session token | `docs/historias_de_usuario.md:49-63` |
| HU-SEG-02 | Mutations isolated by branch (403 on cross-branch write, read-only on other branches, branch derived from token) | `docs/historias_de_usuario.md:66-79` |
| HU-SEG-03 | Admin manages users, roles, branches (branch code uniqueness, role-gated login, soft-disable) | `docs/historias_de_usuario.md:82-95` |
| RF-SEG-01 | Authentication: obtain a session carrying identity/role/branch; must support logout and inactive-session expiry | `docs/especificacion_requerimientos.md:85` |
| RF-SEG-02 | User/role CRUD (register, edit, disable, query), role in {ADMIN, BRANCH_MANAGER, OPERATOR} plus branch assignment | `docs/especificacion_requerimientos.md:86` |
| RF-SEG-03 | Branch CRUD with unique code, name, location | `docs/especificacion_requerimientos.md:87` |
| RF-SEG-04 | Query/filter the audit log by user, branch, entity, action, date range | `docs/especificacion_requerimientos.md:88` |
| RN-08 | Cross-branch inventory queries are strictly read-only except for ADMIN | `docs/especificacion_requerimientos.md:193` |
| RN-14 | The acting branch is always derived from the authenticated session, never a client parameter | `docs/especificacion_requerimientos.md:199` |
| RNF-SEC-01 | RBAC distinguishing at least ADMIN / BRANCH_MANAGER / OPERATOR | `docs/especificacion_requerimientos.md:235` |
| RNF-SEC-02 | JWT/Bearer auth; passwords hashed with a robust algorithm (Argon2 / BCrypt + salt) | `docs/especificacion_requerimientos.md:236` |
| RNF-SEC-03 | Branch-context isolation: mutation only on own branch, read-only elsewhere | `docs/especificacion_requerimientos.md:237` |
| RNF-SEC-04 | TLS in transit outside local development | `docs/especificacion_requerimientos.md:238` |
| RNF-SEC-05 | Input validation; OWASP Top 10 mitigations (parameterized SQL, resource-ownership checks, `external_id` only) | `docs/especificacion_requerimientos.md:239` |
| RNF-SEC-06 | Restricted CORS plus rate limiting on auth endpoints | `docs/especificacion_requerimientos.md:240` |
| RNF-SEC-07 | No secrets in source or repository; environment variables only | `docs/especificacion_requerimientos.md:241` |
| RNF-SEC-08 | Audit/Kardex retention of at least 5 years | `docs/especificacion_requerimientos.md:242` |
| RNF-ESC-02 | Data model and architecture must scale to new branches without a schema change | `docs/especificacion_requerimientos.md:260` |

**Use cases**:

| ID | Summary | Detail level |
|---|---|---|
| CU-SEG-01 | Authenticate — full flow plus 3 exceptions | `docs/casos_de_uso.md:532-555` |
| CU-SEG-02 | Manage users and roles | Catalog entry only — `docs/casos_de_uso.md:102` |
| CU-SEG-03 | Manage branches | Catalog entry only — `docs/casos_de_uso.md:103` |
| CU-SEG-04 | Query the audit log | Catalog entry only — `docs/casos_de_uso.md:104` |
| CU-INV-04 | Cross-branch stock query (consumes IAM's branch-context derivation) | Full flow — `docs/casos_de_uso.md:501-528` |

### Ambiguities and gaps

1. **CU-SEG-02, CU-SEG-03 and CU-SEG-04 have no detailed flow specification** — one catalog row each (`docs/casos_de_uso.md:97-104`). Only CU-SEG-01 received the detailed treatment; the detailed set (`docs/casos_de_uso.md:295-555`) is a representative sample, not an exhaustive one. The spec phase must derive those flows from HU-SEG-03's acceptance criteria and the RF-SEG-02/03/04 text.
2. **RF-SEG-01 contradicts the stateless-JWT decision on "logout" and "inactive session expiry".** RF-SEG-01 requires the system to "permitir el cierre de sesión y expirar sesiones inactivas" (`docs/especificacion_requerimientos.md:85`), while section 3.4 of the architecture document commits to a stateless JWT scheme with no server-side session (`docs/decisiones_arquitectura_tecnica.md:131,134`). A pure stateless JWT cannot be revoked server-side before expiry, and "inactive session expiry" implies a sliding session that stateless JWT does not have. No `sessions` or `refresh_tokens` table exists to back a revocation mechanism today.
3. HU-SEG-03's acceptance criteria require a disabled user's historical movements to stay visible and intact (logical delete, never physical). This confirms `users.is_active` (`backend/init-db/01-init-schema.sql:39`) is the disable flag and that a `users` row must never be deleted.

## 2. Data model already defined

`backend/init-db/01-init-schema.sql`:

- `branches` (`:15-26`): `id BIGINT IDENTITY`, `external_id UUID UNIQUE DEFAULT gen_random_uuid()`, `code VARCHAR(20) UNIQUE`, `name`, `address`, `city`, `phone` (nullable), `is_active BOOLEAN DEFAULT TRUE`, `created_at` / `updated_at TIMESTAMPTZ`. Index on `external_id` (`:28`). The later `ALTER TABLE branches ADD COLUMN default_price_list_id` (`:144-146`) belongs to pricing, not to IAM.
- `users` (`:30-42`): `id BIGINT IDENTITY`, `external_id UUID UNIQUE`, `branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL` (nullable — NULL means corporate admin, `:33`), `username VARCHAR(50) UNIQUE`, `email VARCHAR(100) UNIQUE`, `password_hash VARCHAR(255)`, `full_name VARCHAR(120)`, `role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'BRANCH_MANAGER', 'OPERATOR'))` (`:38` — exact strings, **no `ROLE_` prefix**, verified), `is_active BOOLEAN DEFAULT TRUE`, `created_at` / `updated_at`. Indexes on `external_id`, `branch_id`, `role` (`:44-46`).
- `audit_logs` (`:397-409`): `id`, `external_id UUID UNIQUE`, `user_id BIGINT REFERENCES users(id) ON DELETE SET NULL`, `branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL`, `action VARCHAR(50)` (free text, e.g. `'CREATE_SALE'`), `entity_name VARCHAR(50)`, `entity_id VARCHAR(100)`, `payload_before JSONB`, `payload_after JSONB`, `ip_address VARCHAR(50)`, `created_at`. Indexes on `external_id`, `created_at`, `(entity_name, entity_id)` (`:411-413`).
- **No `sessions`, `refresh_tokens` or `login_attempts` table exists** in `01-init-schema.sql` or `docs/diagrama_er.md`. Auth state lives entirely in the JWT; nothing today backs token revocation, refresh, or DB-level rate limiting.

`backend/init-db/02-seed-data.sql`:

- Seeds 3 branches (`SUC-BOG`, `SUC-MED`, `SUC-CAL`, `external_id` prefix `b`, `:20-23`) and 7 users (`:28-40`): one `ADMIN` with `branch_id = NULL` (corporate), three `BRANCH_MANAGER` (one per branch), three `OPERATOR` (one per branch). All share one BCrypt hash for the password `Password123!` (`:14-15`). Seed `external_id` values use the documented hex-prefix convention (`:7-11`).
- `docs/deuda_tecnica.md:78-92` (DT-02) records this as accepted debt, to be paid alongside DT-01 by moving seeds to a `dev`-only Flyway location. **Out of scope for this change.**

## 3. Current backend state

- `backend/src/main/java/com/optiplant/inventory/InventoryApplication.java` — plain `@SpringBootApplication` with `@ConfigurationPropertiesScan`, no IAM logic (`:1-15`).
- `backend/src/main/java/com/optiplant/inventory/JwtProperties.java` — `@ConfigurationProperties(prefix = "optiplant.jwt")` record with only `secret` (`@NotBlank`, `@Size(min = 32)`). Its javadoc states the class lives in the base package as a stopgap and "migra a `iam/infrastructure/config` cuando ese módulo se construya" (`:9-11`). **This class must move into `iam` as part of this change.**
- `backend/src/main/java/com/optiplant/inventory/SecurityConfig.java` — minimal stateless filter chain: CSRF, HTTP Basic and form login disabled, `SessionCreationPolicy.STATELESS`, permits `/actuator/health*`, `/actuator/info` and the OpenAPI/Swagger paths, everything else `authenticated()` (`:19-40`). Its javadoc states that role rules, when they arrive, will use `hasAuthority()` with `ADMIN`, `BRANCH_MANAGER`, `OPERATOR` — no `ROLE_` prefix (`:14-17`).
- **No JWT library exists in `backend/pom.xml`** (all 20 dependencies checked, `:23-123`): no `jjwt`, no `nimbus-jose-jwt`, no `spring-security-oauth2-resource-server` or `-jose`. Only `spring-boot-starter-security` is present, which brings `spring-security-crypto` (so `BCryptPasswordEncoder` is available) but no JWT signing or parsing. A JWT library must be added — see open question 2.
- `backend/src/test/java/com/optiplant/inventory/ModuleBoundariesTest.java` — the authoritative boundary rules:
  - `elDominioNoConoceInfraestructuraNiFramework` (`:45-54`): no class in `..domain..` may depend on `org.springframework..`, `jakarta.persistence..`, `..application..` or `..infrastructure..`.
  - `laCapaDeAplicacionNoConoceSusAdaptadores` (`:56-64`): no class in `..application..` may depend on `..infrastructure..`.
  - `ningunModuloEntraAlInteriorDeOtro` (`:66-77`): `slices().matching(BASE + ".(*)..")` — every direct subpackage of `com.optiplant.inventory` is one slice — `.should().notDependOnEachOther()`, with `ignoreDependency(alwaysTrue(), resideInAPackage(BASE + ".shared.."))`. **This forbids any direct cross-module import in either direction**; the only legal bridge is `shared`. There is no per-module public-API carve-out.
  - `noHayCiclosEntreModulos` (`:79-87`): no cycles among module slices.
  - `sharedEsUnaHoja` (`:89-97`): no class in `.shared..` may depend on any of the ten module packages.
  - The `MODULOS` array (`:35-38`) already lists `iam`, so the rules apply automatically as soon as `com.optiplant.inventory.iam` classes appear; `allowEmptyShould(true)` only suppresses the check while the package is empty (`:24-28`).
- `backend/src/test/java/com/optiplant/inventory/ApplicationContextIT.java` — full-context Testcontainers test hitting `/actuator/health/readiness`. Its `*IT` naming confirms the failsafe/surefire split any new Docker-dependent IAM test must follow (`:1-47`).

## 4. Canonical structure (architecture document §2.4 and §5)

`docs/decisiones_arquitectura_tecnica.md:78` — the `iam` module owns **"Usuarios, roles, sesiones y bitácora de auditoría"**, use cases CU-SEG-01 through CU-SEG-04. The audit log is therefore IAM-owned, not a separate module.

Canonical per-module hexagonal layout (`docs/decisiones_arquitectura_tecnica.md:247-267`), applied to `iam`:

```
com.optiplant.inventory.iam/
├── domain/
│   ├── model/           # User, Role, Branch, AuditEntry — pure Java, no Spring, no JPA
│   ├── exception/       # business exceptions
│   └── service/         # domain services (RN rules)
├── application/
│   ├── port/in/         # use-case interfaces
│   ├── port/out/        # repository and event-publishing interfaces
│   └── service/         # use-case implementations
└── infrastructure/
    ├── adapter/in/web/          # REST controllers, request/response DTOs
    ├── adapter/out/persistence/ # JPA entities, Spring Data repositories, mappers
    └── config/                  # module Spring configuration (JwtProperties lands here)
```

The existing `shared/` leaf module (`:266`) must stay free of any dependency on `iam` (`ModuleBoundariesTest.java:89-97`), which constrains how `iam` publishes anything other modules consume — see §7.

## 5. Architecture decisions constraining this module

All from `docs/decisiones_arquitectura_tecnica.md` §3.4 (`:128-146`), "Estrategia de Autenticación y Autorización":

- **Stateless JWT** (HMAC-SHA256 or RSA), Spring Security 7, RBAC, branch-context isolation (`:130-131`).
- The token carries the claims `sub`, `role` and `branch_id`; no in-memory session, so multi-instance deployment needs no session affinity — this sustains RNF-ESC-03 (`:134`).
- **Role model** (`:137-141`): `ADMIN` covers corporate scope, configuration and global audit; `BRANCH_MANAGER` covers own-branch supervision, approvals and dashboard; `OPERATOR` covers own-branch execution. The document warns explicitly: "El valor persistido, el *claim* del token y la autoridad de Spring Security deben ser la misma cadena. Introducir un prefijo `ROLE_`... rompe la validación contra el esquema; si se usa `hasRole()`... debe configurarse explícitamente el prefijo vacío o emplearse `hasAuthority()`" (`:143`).
- A security filter injects the token's `branch_id` into each use case's context; **mutations** are restricted to the caller's own branch, while **reads** of other branches' inventory are permitted (`:145`) — sustains RNF-SEC-03, RN-08 and RN-14.
- **Passwords**: BCrypt with a work factor of at least 10, or Argon2id (`:146`) — sustains RNF-SEC-02.
- No refresh-token strategy and no explicit token expiry value appear anywhere in the document — open question 1.
- §3.6 (`:179-193`) — synchronous output ports for atomic effects, `AFTER_COMMIT` domain events for reactions. This governs how `iam` exposes audit writing and branch context to other modules — see §7 and open question 4.

## 6. Technical debt touching IAM, auth or security

- **DT-01** (`docs/deuda_tecnica.md`, referenced from architecture §7 `:302-305`): `init-db/01-init-schema.sql` must eventually become Flyway's `V1__initial_schema.sql`. Flyway migrations must **not** be added alongside `init-db/` today; `backend/src/main/resources/application.yml:24-28` sets `spring.flyway.enabled: false` and cites DT-01. This change consumes the existing schema as-is.
- **DT-02** (`docs/deuda_tecnica.md:78-92`): seed users load with a known password unconditionally — accepted risk, not to be fixed here.
- No debt item covers JWT library choice, refresh tokens or rate limiting. These are unaddressed design gaps rather than tracked debt, which reinforces that the proposal and design phases must decide them explicitly.

## 7. Dependency direction and `shared/`

Every one of the other nine modules (`catalog`, `pricing`, `inventory`, `purchases`, `sales`, `transfers`, `logistics`, `notifications`, `analytics`) will need to know who is calling and from which branch in order to enforce RNF-SEC-03 and RN-14, and the mutation-heavy ones will need to write to `audit_logs`, which `iam` owns.

But `ningunModuloEntraAlInteriorDeOtro` (`ModuleBoundariesTest.java:66-77`) forbids any module from importing `iam`'s classes directly. Consequently the authenticated-principal / branch-context object other modules consult cannot be an `iam.domain` or `iam.application` type reached by direct import. It has to be either:

- **(a)** a type declared in `shared` that `iam`'s infrastructure populates — for example into Spring's `SecurityContextHolder`, as an `Authentication` whose principal is a `shared`-owned value object; or
- **(b)** accessed exclusively through the Spring Security `Authentication` / `SecurityContext` API, which lives in `org.springframework.security` and therefore does not trip the slice rule, with each module owning a thin adapter that reads the claims.

Likewise, if other modules must persist audit entries, the port interface must live in `shared` and be implemented by an `iam` adapter wired by Spring, never imported directly. The architecture document does not specify which shape to use, and the choice materially affects every future module's adapter code — open question 3.

`shared/` must remain a leaf: `iam` may depend on `shared`, never the reverse.

## 8. Open questions for the proposal phase

1. **Logout and inactive-session expiry versus stateless JWT** (RF-SEG-01 versus architecture §3.4) — is logout purely a client-side token discard with a short fixed expiry, or does the project need a revocation mechanism (refresh tokens, denylist table)? No such table exists today; adding one is a schema decision that must be made explicitly.
2. **JWT library selection** — none is a dependency today. Candidates must be evaluated against the API actually resolved for Spring Boot 4.1 and Spring Security 7, never assumed from memory: a standalone signing library (for example `io.jsonwebtoken:jjwt-api` / `-impl` / `-jackson`) versus Spring Security's own `spring-security-oauth2-resource-server` plus `spring-security-oauth2-jose` (Nimbus-backed). The choice affects `SecurityConfig`, `JwtProperties` and how HMAC-SHA256 signing and parsing are implemented.
3. **Cross-module branch and principal propagation shape** (§7) — a `shared`-owned context value object versus reading Spring Security's `Authentication` directly in each module. This must be fixed before any other module's design assumes a shape.
4. **Audit-log write path** — `iam` owns `audit_logs`, but every mutation-heavy module needs to write to it. Does the port live in `shared`, and is it a synchronous output port (per §3.6's atomic-effect rule, if audit completeness is non-negotiable) or an `AFTER_COMMIT` domain event (§3.6 lists alerts and analytics projections in that category, not audit)? The architecture document does not classify audit writes into either bucket.
5. **CU-SEG-02, CU-SEG-03 and CU-SEG-04 detailed flows** must be authored during spec and design from HU-SEG-03's acceptance criteria and the RF-SEG-02/03/04 text, since no detailed use-case section exists.
6. **Rate limiting on auth endpoints (RNF-SEC-06)** — no infrastructure exists for this. A decision is needed on whether it is in scope here or deferred.
7. **Scope boundary of this change** — does `add-iam-module` cover the full branch and user CRUD (CU-SEG-02, CU-SEG-03) and the audit-log query (CU-SEG-04), or only authentication and authorization scaffolding (CU-SEG-01), with the admin CRUD split into a follow-up change? EP-01 bundles all three user stories at `Must` priority for a combined 13 points, which is exactly the threshold the project itself treats as a split candidate (`docs/historias_de_usuario.md:28`).

## 9. Recommendation

Proceed to the proposal phase with the module scoped to the canonical `iam` layout above, reusing the existing `users`, `branches` and `audit_logs` tables unchanged (no new migration, no Flyway), moving `JwtProperties` into `iam/infrastructure/config`, and treating open questions 1 through 4 and 7 as decisions the proposal must make explicit and justify. They are cheap to answer now and expensive to unwind once other modules depend on the chosen shape.

Given the 13-point, three-story size and the review-workload budget, a chained slice plan is recommended: authentication plus JWT filter plus `SecurityConfig` role rules (HU-SEG-01, HU-SEG-02), then user and branch admin CRUD (HU-SEG-03), then the audit-log query endpoint (CU-SEG-04).

## 10. Risks

- Building the JWT filter and `SecurityConfig` rules before deciding the cross-module branch-context shape (open question 3) risks other modules' future adapters being written against an ad hoc shape that must later be reworked.
- Picking a JWT library without recording the decision risks violating the project's rule against naming Spring classes from memory, if the chosen API does not match what is actually resolved under `~/.m2`.
- Treating logout as solved by stateless JWT alone would silently fail RF-SEG-01's explicit text. This needs an explicit accept-or-reject decision in the proposal.
- If the CU-SEG-02/03/04 flows are authored ad hoc during implementation instead of during the spec phase, the traceability validator's RF to CU to HU chain could end up inconsistent with what is built.
