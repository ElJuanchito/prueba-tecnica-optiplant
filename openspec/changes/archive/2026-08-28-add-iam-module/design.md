# Design: `add-iam-module`

## Verification status of Spring type names

**Resolved in Slice 1 apply (tasks 1.1–1.3).** `./mvnw dependency:go-offline` ran clean;
`./mvnw dependency:tree` confirmed `spring-boot-starter-security-oauth2-resource-server:4.1.1`
(the `security-`prefixed starter, BOM `:2748`/`:2753`) is the one that brings
`spring-security-oauth2-jose:7.1.1` and `spring-security-oauth2-resource-server:7.1.1` — the
differently-named `spring-boot-starter-oauth2-resource-server` (`:2643`) was never added and was
not needed. Every previously `⟪UNRESOLVED⟫` type in the `SecurityConfig` block below is now a
real, JAR-confirmed name (see the resolved-types table further down). The paragraph immediately
below is preserved as a historical record of the design-phase limitation that apply then closed.

**Blocking limitation, stated openly (design-phase; closed by apply).** This phase had no shell access, so
`./mvnw dependency:go-offline` could not be run. Type names were verified by
probing the JAR central directories already present in `~/.m2` (a literal
`path/Name.class` match means the entry exists).

| Type | Status |
|---|---|
| `org.springframework.security.core.Authentication`, `...core.context.SecurityContextHolder`, `...core.authority.SimpleGrantedAuthority`, `...core.userdetails.UserDetails`, `...authentication.AbstractAuthenticationToken` | **Verified** in `spring-security-core-7.1.1.jar` |
| `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder` | **Verified** in `spring-security-crypto-7.1.1.jar` |
| `org.springframework.security.web.SecurityFilterChain`, `...web.AuthenticationEntryPoint`, `...web.access.AccessDeniedHandler` | **Verified** in `spring-security-web-7.1.1.jar` |
| `org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer`, `...web.configurers.CorsConfigurer`, `...method.configuration.EnableMethodSecurity` | **Verified** in `spring-security-config-7.1.1.jar` |
| `org.springframework.web.cors.{CorsConfiguration, CorsConfigurationSource, UrlBasedCorsConfigurationSource}` | **Verified** in `spring-web-7.0.9.jar` |
| `org.springframework.boot.context.properties.bind.DefaultValue`, `org.springframework.boot.convert.DurationUnit` | **Verified** in `spring-boot-4.1.1.jar` |
| Everything in `org.springframework.security.oauth2..` — decoder, encoder, claim-set builder, authentication converter, bearer filter | **UNRESOLVED.** `spring-security-oauth2-jose` / `-resource-server` are **absent from `~/.m2`** (negative control: `NimbusJwtDecoder.class` matched **zero** files under `~/.m2/.../org/springframework/security`). Neither starter POM is downloaded either. |

BOM coordinates re-verified this phase: `spring-boot-starter-security-oauth2-resource-server`
(`spring-boot-dependencies-4.1.1.pom:2748`) and `-test` (`:2753`), both `4.1.1`;
`spring-security-oauth2-{core,jose,resource-server}` managed at `spring-security-bom-7.1.1.pom:128,133,138`.

> **Note for apply**: the BOM ALSO manages a differently-named
> `spring-boot-starter-oauth2-resource-server` (`:2643`). Two starters, similar names.
> Slice 1 must add the `security-`prefixed pair the proposal chose, then run
> `./mvnw dependency:tree` and confirm `spring-security-oauth2-jose` is on the
> classpath (the JOSE module is what supplies HMAC signing).

**Rule for apply**: every `org.springframework.security.oauth2..` symbol below is written as
`⟪UNRESOLVED: role⟫`. Apply MUST resolve the dependency first and read the real name off the
resolved JAR. Do not substitute a remembered Boot 3 name — `CLAUDE.md` records that exact failure
mode (`spring-boot-starter-web` → `-webmvc`, `TestRestTemplate`'s package move).

## Technical Approach

`iam` is the first business module, in the canonical hexagonal layout of architecture §5
(`decisiones_arquitectura_tecnica.md:247-267`). Access-token validation stays stateless
(no DB hit per request, preserving RNF-ESC-03); only `/api/auth/refresh` and `/api/auth/logout`
touch the new `refresh_tokens` table. Cross-module coupling happens exclusively through a new
`shared` leaf package: a framework-free principal record, a principal-accessor interface, and a
synchronous audit output port. Neither `shared` nor any other module ever imports an `iam` type.

## Package layout

`shared` does **not exist yet** — no `com/optiplant/inventory/shared/` directory is present today
(the whole tree is `InventoryApplication`, `JwtProperties`, `SecurityConfig`). Slice 1 creates it.

```
com.optiplant.inventory
├── InventoryApplication.java                     (unchanged; @ConfigurationPropertiesScan
│                                                  still reaches iam.infrastructure.config)
├── shared/                                        ← slice 1 · leaf, framework-free
│   ├── security/  AuthenticatedPrincipal · Role · PrincipalAccessor
│   └── audit/     AuditWritePort · AuditEntryCommand · AuditAction
└── iam/
    ├── domain/
    │   ├── model/      UserAccount · BranchProfile · RefreshTokenGrant ·
    │   │               RefreshTokenState · RevocationReason · AuditRecord
    │   ├── exception/  InvalidCredentialsException · RefreshTokenRejectedException ·
    │   │               UserDisabledException · CrossBranchMutationException ·
    │   │               DuplicateBranchCodeException · DuplicateUsernameException
    │   └── service/    RefreshTokenPolicy · BranchAccessPolicy
    ├── application/
    │   ├── port/in/    AuthenticateUseCase · RefreshSessionUseCase · LogoutUseCase ·
    │   │               ManageUsersUseCase · ManageBranchesUseCase · QueryAuditLogUseCase
    │   ├── port/out/   UserRepositoryPort · BranchRepositoryPort · RefreshTokenRepositoryPort ·
    │   │               AuditQueryPort · AccessTokenIssuerPort · PasswordHasherPort ·
    │   │               SecretTokenGeneratorPort · LoginThrottlePort
    │   └── service/    AuthenticationService · SessionRefreshService · LogoutService ·
    │                   UserAdminService · BranchAdminService · AuditQueryService
    └── infrastructure/
        ├── adapter/in/web/           AuthController · UserAdminController ·
        │                             BranchAdminController · AuditLogController ·
        │                             request/response DTOs · IamExceptionHandler
        ├── adapter/out/persistence/  UserJpaEntity · BranchJpaEntity · RefreshTokenJpaEntity ·
        │                             AuditLogJpaEntity · *SpringDataRepository ·
        │                             *PersistenceAdapter (incl. AuditWriteAdapter) · MapStruct mappers
        ├── adapter/out/security/     JwtAccessTokenAdapter · BCryptPasswordHasher ·
        │                             SecureRandomTokenGenerator · Sha256TokenDigest ·
        │                             SecurityContextPrincipalAccessor ·
        │                             InMemoryLoginThrottle
        └── config/                   SecurityConfig · JwtProperties · CorsProperties ·
                                      IamSecurityBeans
```

Per slice: **1** = `shared/**` + `iam/infrastructure/config/JwtProperties`; **2** = domain,
auth ports/services, `adapter/out/security/**`, `adapter/out/persistence` (user + refresh token),
`AuthController`, `SecurityConfig`; **3** = `BranchAccessPolicy`, `IamExceptionHandler`,
`SecurityConfig` authority rules; **4** = `shared/audit/**` + `AuditWriteAdapter` +
`AuditQueryService`/`AuditLogController`; **5** = `UserAdmin*` / `BranchAdmin*`.

### Why no `config/` or `util/` package appears

`ningunModuloEntraAlInteriorDeOtro` matches `com.optiplant.inventory.(*)..`
(`ModuleBoundariesTest.java:69`), so every direct subpackage is a module slice. Only `shared` and
`iam` are added — both legitimate. `InventoryApplication` stays directly in the base package,
where the slice pattern does not reach it.

## Architecture Decisions

### Decision: `SecurityConfig` and `JwtProperties` move into `iam/infrastructure/config`

**Choice**: both move; the base package keeps only `InventoryApplication`.
**Alternatives**: keep them in the base package (status quo); split — rules in `iam`, chain in base.
**Rationale**: `JwtProperties.java:9-11` already declares the base package a stopgap and names
`iam/infrastructure/config` as its destination. `SecurityConfig.java:14-17` says the authorization
map "pertenece al módulo `iam`". The base-package rule exists because a non-module subpackage
would add an undeclared boundary — `iam.infrastructure.config` is a real module subpackage, so the
rule does not apply. Decisive technical reason: `SecurityConfig` must inject the decoder and
principal-converter beans, which are `iam` types; leaving it in the base package would put `iam`
imports in a class that no boundary rule governs, hiding the coupling from ArchUnit.
`@ConfigurationPropertiesScan` on `InventoryApplication` (`InventoryApplication.java:8`) and the
`@SpringBootApplication` component scan (`:7`) both cover the base package **and its subpackages**,
so `JwtProperties` binding and the package-private `SecurityConfig` bean (`SecurityConfig.java:21`)
keep working from their new home without any extra annotation.
**Consequence accepted**: `SecurityConfig` will list URL patterns belonging to future modules.
Those are strings, not imports — no boundary is crossed. If it grows unwieldy, each module can
later contribute its own ordered `SecurityFilterChain` bean; not needed while `iam` is alone.

### Decision: principal accessor is an interface in `shared`, implemented in `iam`

**Choice**: `shared.security.PrincipalAccessor` (interface, JDK types only) + `iam...security.SecurityContextPrincipalAccessor` (reads `SecurityContextHolder`).
**Alternatives**: (a) static utility in `shared` reading `SecurityContextHolder` directly;
(b) every module parses claims off `Authentication` itself (explore.md:129).
**Rationale**: (b) scatters claim-string handling across nine modules and leaves RN-14 without a
single testable enforcement point. (a) works and would not break `sharedEsUnaHoja` (that rule only
forbids `shared` → the ten module packages, `ModuleBoundariesTest.java:93`), but drags
spring-security into `shared` and forces every consumer's unit test to prime a static holder. With
the interface, a consumer stubs one method. All Spring-Security coupling stays inside `iam`.
**Boundary proof**: `shared` imports only `java.util.UUID`/`java.time` → `sharedEsUnaHoja` holds
vacuously. Consumers import `shared` only → exempted by
`ignoreDependency(alwaysTrue(), resideInAPackage(BASE + ".shared.."))` (`:73`). `iam` → `shared`
is the same exempted edge. No `X → iam` or `iam → X` edge exists in either direction, and no cycle.

### Decision: the principal carries UUIDs only, never numeric ids

**Choice**: `AuthenticatedPrincipal` holds `users.external_id` and `branches.external_id`.
**Alternatives**: put `users.id` / `branches.id` in the JWT to save a lookup on audit writes.
**Rationale**: a JWT payload is base64, not encrypted — a numeric-id claim publishes the internal
key to every client, contradicting the external-id-only rule. Cost: the audit adapter resolves
UUID → BIGINT before inserting (`audit_logs.user_id` / `branch_id` are BIGINT,
`01-init-schema.sql:400-401`). That is one indexed lookup (`idx_users_external_id`, `:44`) inside
a transaction that is already writing.

### Decision: refresh tokens are stored as a deterministic SHA-256 hex digest, not BCrypt

**Choice**: `token_hash VARCHAR(64)` = lowercase SHA-256 hex of the raw token.
**Alternatives**: BCrypt, mirroring `users.password_hash` (`:36`).
**Rationale**: BCrypt is salted and therefore non-deterministic — `WHERE token_hash = ?` is
impossible, and the lookup would degrade to scanning every live token per refresh, with no UNIQUE
constraint available. A refresh token is 256 bits of `SecureRandom`, not a low-entropy human
secret, so there is no offline-brute-force margin for a slow KDF to buy. The raw token still never
reaches the database.

### Decision: reuse detection revokes the token *family*, not the whole user

**Choice**: `family_id UUID` constant across rotations of one login; reuse revokes that family.
**Alternatives**: revoke every token of the user; revoke nothing and just reject.
**Rationale**: P4 allows multi-device sessions; revoking every token would log out the warehouse
tablet because a phone's token leaked. Family-scoped revocation is the OAuth security-BCP response
and preserves P4. Rejecting without revoking leaves the attacker's copy live until expiry.

### Decision: the inactivity window lives in configuration, not in the schema

**Choice**: `refresh_tokens` stores `last_used_at` and an absolute `expires_at`; the 8-hour idle
window is `JwtProperties.refreshInactivity`, evaluated in `RefreshTokenPolicy`.
**Alternatives**: a generated column or a CHECK encoding 8 hours.
**Rationale**: P1's two TTLs are configuration, not constants. Baking 8 hours into DDL would need
a schema edit to change, and `init-db/` is not migratable today (DT-01).

### Decision: in-memory login throttle, per instance, stated as a limitation

**Choice**: `LoginThrottlePort` (application port) + `InMemoryLoginThrottle` — fixed window,
`ConcurrentHashMap<String, Window>`, keyed on `lower(username) + "|" + clientIp`, default 5
attempts / 5 minutes, cleared on success, `429` when exceeded, background eviction of stale keys.
**Alternatives**: DB-backed `login_attempts` table; gateway/Redis limiter.
**Rationale**: RNF-SEC-06 must be satisfied, not merely tracked. A DB table would add a 21st table
and a write on every failed login — an unauthenticated write amplifier. **Openly limited**: with N
instances the effective ceiling is N × 5; the port exists precisely so a distributed
implementation replaces the adapter without touching `AuthenticationService`. No DT item is
created (proposal decision 5).
**Keying caveat for apply**: `clientIp` comes from the request, so behind a proxy every request
shares one IP. Design position: key on username **and** IP, so a shared IP cannot lock out other
users; do not trust `X-Forwarded-For` unless a forward-header strategy is configured explicitly.

## Interfaces / Contracts

```java
package com.optiplant.inventory.shared.security;

/** The three strings persisted by users.role (01-init-schema.sql:38). No ROLE_ prefix:
 *  the value in the DB, the token claim and the Spring authority are one string. */
public enum Role { ADMIN, BRANCH_MANAGER, OPERATOR }

/** branchId is null for a corporate ADMIN (users.branch_id is nullable, :33). */
public record AuthenticatedPrincipal(UUID userId, String username, Role role, UUID branchId) {

    public boolean isCorporate() { return branchId == null; }

    /** RN-14 + RN-08: mutation only on the session's own branch; ADMIN is corporate-wide. */
    public boolean mayMutateBranch(UUID target) {
        return role == Role.ADMIN || (branchId != null && branchId.equals(target));
    }
}

public interface PrincipalAccessor {
    Optional<AuthenticatedPrincipal> current();
    AuthenticatedPrincipal require();   // throws when unauthenticated
}
```

```java
package com.optiplant.inventory.shared.audit;

public record AuditEntryCommand(
        UUID actorUserId, UUID branchId, String action,
        String entityName, String entityId,
        String payloadBefore, String payloadAfter, String ipAddress) {}

/** Synchronous: the implementation runs inside the caller's transaction.
 *  CLAUDE.md — atomic effects go through a synchronous output port, never an event. */
public interface AuditWritePort {
    void record(AuditEntryCommand command);
}
```

Both files import only `java.util` / `java.time`: `sharedEsUnaHoja` cannot fail on them.

`JwtProperties`, moved to `com.optiplant.inventory.iam.infrastructure.config`, keeps its
`@Validated @ConfigurationProperties(prefix = "optiplant.jwt")` record shape and its existing
`@NotBlank @Size(min = 32)` secret (`JwtProperties.java:25-26`), adding
`@DefaultValue("15m") @NotNull Duration accessTtl` and
`@DefaultValue("8h") @NotNull Duration refreshInactivity`, plus
`@DefaultValue("7d") @NotNull Duration refreshAbsolute` for the absolute cap. `@DefaultValue` is
`org.springframework.boot.context.properties.bind.DefaultValue` — **verified present** in
`spring-boot-4.1.1.jar`. `application-dev.yml:6` and `application-prod.yml:6` keep their existing
`secret` lines untouched, so the `JWT_SECRET`-without-`=` behaviour of `compose.yml` does not
regress; the three durations are added under `optiplant.jwt` only if a non-default is wanted.

## Data Flow

```
LOGIN                                              (POST /api/auth/login, permitAll)
  AuthController ─→ AuthenticationService
        │  1. LoginThrottlePort.check(user|ip)          → 429 if over window
        │  2. UserRepositoryPort.findByUsername         → is_active must be TRUE
        │  3. PasswordHasherPort.matches(raw, hash)     → BCrypt (verified type)
        │  4. AccessTokenIssuerPort.issue(principal)    → signed JWT, 15 min
        │  5. SecretTokenGeneratorPort.generate()       → 256-bit random (raw, returned once)
        │  6. RefreshTokenRepositoryPort.persist(sha256(raw), new family_id, expires_at)
        └─ 200 { accessToken, refreshToken, expiresIn, role, branchId(uuid|null) }

AUTHENTICATED REQUEST                              (any module)
  bearer filter ⟪UNRESOLVED⟫ ─→ decoder ⟪UNRESOLVED⟫ ─→ IamPrincipalConverter
        └─ Authentication{ principal = shared AuthenticatedPrincipal,
                           authorities = [SimpleGrantedAuthority(role.name())] }
                                    │
   module X controller ─→ PrincipalAccessor.require()  (imports shared only)
                                    │
                          principal.mayMutateBranch(target)  → 403 when false

REFRESH                                            (POST /api/auth/refresh, permitAll)
  SessionRefreshService, one transaction:
    lookup by sha256(presented) ─┬─ not found        → 401
                                 ├─ revoked          → REUSE: revoke whole family, 401
                                 ├─ expires_at past  → 401
                                 ├─ last_used_at + refreshInactivity past → 401 (idle)
                                 └─ valid → revoke it (ROTATED) + insert successor
                                            (same family_id, last_used_at = now)
                                          → new access + new refresh

LOGOUT  → revoke the presented token (LOGOUT). Other devices survive (P4).
DISABLE USER (slice 5) → is_active = FALSE + revoke every live token of that user
                         (USER_DISABLED), same transaction. Live access token dies
                         within 15 min (P2).

AUDIT WRITE (slice 4)
  any module's use case (@Transactional)
        └─ AuditWritePort.record(cmd)          ← shared interface
              └─ iam AuditWriteAdapter — resolves UUID→BIGINT, INSERT INTO audit_logs
                 SAME transaction, no @Async, no AFTER_COMMIT, no new transaction
```

## Persistence: `refresh_tokens`

Insert into `backend/init-db/01-init-schema.sql` after `idx_users_role` (`:46`) and before the
`2. MÓDULO: CATÁLOGO MAESTRO` header (`:48`).

```sql
-- Sesiones de refresco. Guarda solo el digest del token, nunca el valor crudo.
-- family_id encadena las rotaciones de un mismo inicio de sesión: si un token ya
-- rotado se vuelve a presentar, se revoca la familia completa y no las demás
-- sesiones del usuario (varios dispositivos simultáneos son válidos).
CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    -- SHA-256 en hexadecimal: determinista, de modo que la búsqueda por hash es
    -- un acceso por índice único. BCrypt, al llevar sal, no permitiría buscarlo.
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(20) CHECK (revoked_reason IN ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'USER_DISABLED')),
    CONSTRAINT chk_refresh_tokens_revocacion CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT chk_refresh_tokens_vigencia CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_tokens_external_id ON refresh_tokens(external_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
```

`ON DELETE CASCADE` departs from the `ON DELETE SET NULL` used by every other FK to `users`
(`:33`, `:400`) on purpose: those preserve history, and a refresh token has none. `user_id` is
`NOT NULL` for the same reason. HU-SEG-03 forbids physically deleting a user anyway, so the clause
is a safety net, not a code path. No unique constraint on `user_id` — P4. No seed rows:
`02-seed-data.sql` is untouched, so no new hex prefix is consumed.

## `scripts/validar_esquema.sh`

One edit at `:78`, then six new checks appended to section **C. Seguridad y roles** after `:94`,
in the script's existing helper style (`rechaza` / `acepta` / `igual`, `:24-52`).

```bash
igual "20 tablas creadas" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" "20"
```

```bash
rechaza "RF-SEG-01 · un refresh token exige su hash" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (1, gen_random_uuid(), NULL, CURRENT_TIMESTAMP + INTERVAL '8 hours')"
rechaza "el hash de un refresh token debe ser un SHA-256 en hexadecimal" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (1, gen_random_uuid(), 'no-es-un-hash', CURRENT_TIMESTAMP + INTERVAL '8 hours')"
rechaza "dos sesiones no pueden compartir el mismo hash" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (1, gen_random_uuid(), repeat('a',64), CURRENT_TIMESTAMP + INTERVAL '8 hours'), (2, gen_random_uuid(), repeat('a',64), CURRENT_TIMESTAMP + INTERVAL '8 hours')"
rechaza "un refresh token no puede colgar de un usuario inexistente" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (9999, gen_random_uuid(), repeat('b',64), CURRENT_TIMESTAMP + INTERVAL '8 hours')"
rechaza "revocar exige registrar el motivo" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at, revoked_at) VALUES (1, gen_random_uuid(), repeat('c',64), CURRENT_TIMESTAMP + INTERVAL '8 hours', CURRENT_TIMESTAMP)"
acepta "un mismo usuario sostiene varias sesiones simultáneas" \
  "INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at) VALUES (1, gen_random_uuid(), repeat('d',64), CURRENT_TIMESTAMP + INTERVAL '8 hours'), (1, gen_random_uuid(), repeat('e',64), CURRENT_TIMESTAMP + INTERVAL '8 hours')"
```

Deliberate: the hex-format check is a `rechaza` on a bad literal, not an `igual` counting bad rows.
On an empty table the counting form would pass vacuously and prove nothing. The `9999` user id is
safe because `02-seed-data.sql` seeds exactly 7 users. Each `rechaza` statement fails atomically,
so it leaves no residue for the next one; only the final `acepta` leaves rows, and the container is
discarded on exit (`trap limpiar EXIT`, `:19`).

**Stale-count consequence.** The script currently performs exactly 19 checks
(2 + 1 + 3 + 2 + 6 + 3 + 2) and prints `$OK comprobaciones correctas` (`:134`). Six more makes 25,
so two documents that quote "19 invariantes" go stale: `CLAUDE.md` (Verificación block) and
`openspec/config.yaml:46`. Both are one-word edits and belong to slice 1. The `19` at `:78` is the
table count, an unrelated coincidence.

## `docs/diagrama_er.md`

Two diagrams must stay consistent. **Mermaid** (§2) — relation next to `USERS ||--o{ AUDIT_LOGS`
(`:70`), entity block after `USERS` (`:82-90`), matching the abbreviated style of that section:

```mermaid
    USERS ||--o{ REFRESH_TOKENS : "mantiene"

    REFRESH_TOKENS {
        bigint id PK
        uuid external_id UK
        bigint user_id FK
        uuid family_id
        varchar token_hash UK
        timestamptz last_used_at
        timestamptz expires_at
        timestamptz revoked_at
    }
```

**PlantUML** (§3) — new entity inside `package "IAM & Organización"`, after the `users` entity and
before the closing brace (`:316-317`):

```plantuml
    entity "refresh_tokens" as refresh_tokens {
        * id : BIGINT <<PK>>
        --
        * external_id : UUID <<UK>>
        * user_id : BIGINT <<FK>>
        * family_id : UUID
        * token_hash : VARCHAR(64) <<UK>>
        * issued_at : TIMESTAMPTZ
        * last_used_at : TIMESTAMPTZ
        * expires_at : TIMESTAMPTZ
        revoked_at : TIMESTAMPTZ
        revoked_reason : VARCHAR(20)
    }
```

Relation, appended to the `users ||--o{ ...` group (after `:598`):

```plantuml
users ||--o{ refresh_tokens : "mantiene"
```

Optional one-line addition to §1's bullet list (`:10-13`), in the same register: *«**Sesiones
revocables:** `refresh_tokens` guarda únicamente el digest del token; la rotación cierra el
anterior y la reutilización de uno ya rotado revoca la familia completa.»* No `RF`/`RNF`/`RN`
identifier is introduced, so `validar_trazabilidad.py` is unaffected.

## `SecurityConfig` (slice 2 wiring, slice 3 rules)

Stays package-private, keeps CSRF/httpBasic/formLogin disabled and `SessionCreationPolicy.STATELESS`
(`SecurityConfig.java:27-30`), keeps the actuator and springdoc `permitAll` matchers (`:32-35`) and
the terminal `anyRequest().authenticated()` (`:37`). Additions:

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))   // verified: CorsConfigurer
.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt
        .decoder(NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build())
        .jwtAuthenticationConverter(iamPrincipalConverter)))
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
        .requestMatchers("/api/auth/logout").authenticated()
        .requestMatchers("/api/admin/users/**", "/api/admin/branches/**").hasAuthority("ADMIN")
        .requestMatchers("/api/audit/**").hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
        .anyRequest().authenticated())
```

**Resolved in slice 1** (task 1.2/1.3) — every type below comes from the JAR actually resolved by
`spring-boot-starter-security-oauth2-resource-server:4.1.1` (confirmed by `dependency:tree`, which
places `spring-security-oauth2-jose:7.1.1` and `spring-security-oauth2-resource-server:7.1.1`
under that exact starter, not under the differently named `spring-boot-starter-oauth2-resource-server`):

| Role | Real type (verified against the resolved JAR) |
|---|---|
| HMAC decoder | `org.springframework.security.oauth2.jwt.NimbusJwtDecoder`, built via the static factory `NimbusJwtDecoder.withSecretKey(SecretKey)` → `NimbusJwtDecoder.SecretKeyJwtDecoderBuilder`, `.macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)`, `.build()` |
| Decoder interface | `org.springframework.security.oauth2.jwt.JwtDecoder` (what `JwtConfigurer.decoder(JwtDecoder)` accepts) |
| Encoder (for issuing access tokens, slice 2) | `org.springframework.security.oauth2.jwt.NimbusJwtEncoder`, built via `NimbusJwtEncoder.withSecretKey(SecretKey)` → `NimbusJwtEncoder.SecretKeyJwtEncoderBuilder`; encodes `JwtEncoderParameters.from(JwtClaimsSet)` |
| Claim-set builder | `org.springframework.security.oauth2.jwt.JwtClaimsSet`, `JwtClaimsSet.builder()...build()` |
| Authentication converter (`.jwtAuthenticationConverter(...)`) | `org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter` implements `Converter<Jwt, AbstractAuthenticationToken>`; `IamPrincipalConverter` (slice 2) wraps/replaces it via `setJwtAuthenticationConverter`/direct `Converter<Jwt, AbstractAuthenticationToken>` implementation to produce `Authentication{principal = shared.AuthenticatedPrincipal}` |
| Bearer filter (installed automatically by `oauth2ResourceServer(...).jwt(...)`, not instantiated by hand) | `org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter` |
| `JwtConfigurer` DSL (verified method signatures) | `.decoder(JwtDecoder)`, `.jwtAuthenticationConverter(Converter<Jwt, ? extends AbstractAuthenticationToken>)` — both confirmed present on `OAuth2ResourceServerConfigurer.JwtConfigurer` in `spring-security-config-7.1.1.jar` |

`OAuth2ResourceServerConfigurer` is **verified present** in `spring-security-config-7.1.1.jar`, and
the `oauth2ResourceServer(...)` entry point plus every nested type above is now confirmed against
the resolved JAR — no more `⟪UNRESOLVED⟫` markers remain in this block. `hasAuthority` /
`hasAnyAuthority` with the bare strings, never `hasRole` — `hasRole` prepends `ROLE_`, which is
exactly what the `users.role` CHECK rejects (`01-init-schema.sql:38`). CORS:
`UrlBasedCorsConfigurationSource` (verified in `spring-web-7.0.9.jar`) with an explicit origin list
from a new `CorsProperties` (`optiplant.cors.allowed-origins`, no wildcard, credentials off),
methods `GET/POST/PUT/PATCH/DELETE`, header `Authorization` — RNF-SEC-06. A javadoc line must
record why an OAuth2-named starter backs a first-party token: it is used as a JOSE/JWT toolkit and
resource-server filter, not as an OAuth2 authorization-server integration (proposal risk row).

## File Changes

| File | Action | Slice |
|---|---|---|
| `backend/init-db/01-init-schema.sql` | Modify — `refresh_tokens` + 4 indexes at `:47` | 1 |
| `scripts/validar_esquema.sh` | Modify — `:78` `19`→`20`; 6 checks in section C | 1 |
| `docs/diagrama_er.md` | Modify — Mermaid entity + relation; PlantUML entity + relation | 1 |
| `CLAUDE.md`, `openspec/config.yaml` | Modify — "19 invariantes" → 25 | 1 |
| `backend/pom.xml` | Modify — 2 BOM-managed deps | 1 |
| `backend/src/main/java/.../shared/security/*`, `shared/audit/*` | Create | 1 / 4 |
| `backend/src/main/java/.../JwtProperties.java` | Move → `iam/infrastructure/config/`, + 3 durations | 1 |
| `backend/src/main/java/.../SecurityConfig.java` | Move → `iam/infrastructure/config/`, + bearer/CORS/authorities | 2 / 3 |
| `backend/src/main/java/.../iam/**` | Create | 2–5 |
| `backend/init-db/02-seed-data.sql`, `docs/casos_de_uso.md`, `docs/deuda_tecnica.md` | **No change** | — |

## Testing Strategy

`*Test` runs in `package` (surefire) and MUST NOT need Docker; `*IT` runs in `verify` (failsafe).
With Data JPA on the classpath a `@SpringBootTest` without a database cannot build a context, so
anything Spring-contextual is an `IT` (`ApplicationContextIT.java:16-19`). Integration tests use
`RestClient` from `spring-web`, not `TestRestTemplate` (`:21-23`).

| Slice | Surefire (`*Test`, no Docker) | Failsafe (`*IT`, Testcontainers) |
|---|---|---|
| 1 | `ModuleBoundariesTest` (now non-vacuous), `AuthenticatedPrincipalTest` (RN-08/RN-14 truth table incl. corporate `branchId == null`), `SharedIsFrameworkFreeTest` (ArchUnit: no `org.springframework..` under `shared`) | `ApplicationContextIT` still green after the `JwtProperties` move |
| 2 | `RefreshTokenPolicyTest` (idle / absolute / revoked / reuse, fixed `Clock`), `LoginRateLimitTest`, `Sha256TokenDigestTest` | `AuthenticationFlowIT` — login → call → refresh rotates → old token 401 → logout → 401; wrong password 401; disabled user 401 |
| 3 | `BranchAccessPolicyTest` | `BranchIsolationIT` — cross-branch mutation 403, cross-branch read 200, ADMIN mutates anywhere, no endpoint accepts a branch parameter |
| 4 | `AuditEntryCommandTest` | `AuditLogQueryIT` — the five RF-SEG-04 filters; `AuditAtomicityIT` — a use case that throws after `AuditWritePort.record` leaves **zero** `audit_logs` rows (proves the write is inside the caller's transaction) |
| 5 | `UserAdminServiceTest`, `BranchAdminServiceTest` | `UserAdminIT` (disable revokes every live token, P2/P4; no physical delete), `BranchAdminIT` (duplicate `code` 409) |

Cross-cutting greps that must stay empty and belong in the verify phase: `ROLE_` anywhere under
`backend/src`; `hasRole(` in `SecurityConfig`; any numeric id in a response DTO.

`AuditAtomicityIT` is the load-bearing one: it is the only test that can distinguish the required
synchronous port from an accidental `AFTER_COMMIT` or `@Async` implementation.

## Threat Matrix

**N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification or
process-integration boundary.** `validar_esquema.sh` is edited, but only by adding SQL literals to
existing helper calls; no argument composition, cwd selection, repository selection or process
invocation changes. HTTP authorization is a security boundary, covered above and by
`BranchIsolationIT` / `AuthenticationFlowIT`, not by this matrix.

## Migration / Rollout

No data migration. `refresh_tokens` starts empty and needs no backfill. `init-db/` only runs on a
fresh volume, so any developer with an existing volume must `docker compose down -v` before the
next start — the same constraint the rollback plan already records. Flyway stays disabled
(`application.yml:24-28`, DT-01); no migration file is added.

## Open Questions

- [x] **Resolved in slice 1 apply**: every `org.springframework.security.oauth2..` type name is
      now confirmed against the resolved JAR (see the resolved-types table in the `SecurityConfig`
      section). Slice 2 must use those exact names — never a remembered Boot 3 name.
- [x] **Resolved in slice 1 apply**: `spring-boot-starter-security-oauth2-resource-server` (`:2748`)
      is the starter that brings `spring-security-oauth2-jose`, confirmed with
      `./mvnw dependency:tree`. The `:2643` starter was not added.
- [ ] Absolute refresh cap defaulted to 7 days here. P1 fixes the 15-minute access TTL and the
      8-hour idle window but is silent on an absolute ceiling; 7 days is a design proposal, cheap
      to change since it is configuration.
- [ ] `AuditEntryCommand` carries `payloadBefore` / `payloadAfter` as `String` (JSON) because
      `audit_logs` types them `JSONB` (`:405-406`) and `shared` must stay free of a JSON-library
      type. Serialization therefore happens in the caller. Acceptable, but it is the one place
      where the shared port leaks a format concern.
