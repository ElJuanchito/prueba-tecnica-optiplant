# Apply Progress: `add-catalog-module`

## Phase 1 — S1: Schema, validator and the `shared` port (PR1)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-02-s1-esquema`.
**Status**: 13/13 tasks complete. Ready for verify.

### Completed tasks

- [x] 1.1 `categories` gains `is_active BOOLEAN NOT NULL DEFAULT TRUE` and `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` — table block now matches design §10.1 edit 1 verbatim.
- [x] 1.2 `CREATE UNIQUE INDEX uq_categories_name_ci ON categories (LOWER(name));` added right after `idx_categories_external_id`, with the four-line Spanish comment from design §10.1 edit 2. The column's pre-existing `UNIQUE` on `name` is kept (change stays additive).
- [x] 1.3 `CREATE UNIQUE INDEX uq_product_units_single_default ON product_units(product_id) WHERE is_default_sale_unit;` added after `idx_product_units_product`, with its Spanish comment (design §10.1 edit 3). `idx_product_units_product` already existed at `:117`, so only the partial unique index is new.
- [x] 1.4 `02-seed-data.sql` NOT edited. Verified: `:45` inserts categories with explicit list `(external_id, name, description)` so S-1/S-2 take defaults; `product_units` `:58-65` has exactly one `is_default_sale_unit = TRUE` per product (1→SACO_50KG, 2→GALON_4L, 3→BOLSA_INDIVIDUAL, 4→PAQUETE_1KG, 5→ROLLO_500M); the four category names at `:46-49` have no case-insensitive collision.
- [x] 1.5 Section `G. Catálogo maestro` appended to `scripts/validar_esquema.sh` after section F, before the summary block, with the five checks verbatim from design §10.3 (2×`igual`, 2×`rechaza`, 1×`acepta`). Check count 25 → 30.
- [x] 1.6 `CLAUDE.md:15` "25 invariantes" → "30 invariantes"; `openspec/config.yaml:46` "Checks 25 invariants" → "Checks 30 invariants".
- [x] 1.7 `./scripts/validar_esquema.sh` run against a fresh `postgres:17-alpine` container — 30/30 checks green (see gate output below). `igual "20 tablas creadas"` still `(20)`; `02-seed-data.sql` loads; section G all green.
- [x] 1.8 `docs/diagrama_er.md`: Mermaid `CATEGORIES` block gains `boolean is_active`; PlantUML `categories` entity gains `* is_active : BOOLEAN` and `updated_at : TIMESTAMPTZ` (mirrors the existing `products` entity style). Spanish untouched, no `RF`/`RNF`/`RN` identifier introduced.
- [x] 1.9 `backend/src/main/java/com/optiplant/inventory/shared/stock/ProductStockPresencePort.java` created — one method `boolean isProductUntouched(UUID productExternalId)`, sole import `java.util.UUID`, Javadoc pins the exact two-clause `(a)`/`(b)` predicate of contract §2.2 (including the non-redundancy of clause (b) / RN-13) and the fail-closed / single-question (PA-09) constraints.
- [x] 1.10 `shared/audit/AuditAction.java`: enum now `CREATE, UPDATE, DISABLE, ENABLE, DELETE`; Javadoc rewritten to say it holds generic CRUD verbs while `AuditEntryCommand.action()` stays a `String` for module-specific names, and that no `switch` dispatches on it so adding constants is safe (design D-9).
- [x] 1.11 `cd backend && ./mvnw test -Dtest=SharedIsFrameworkFreeTest,ModuleBoundariesTest` — BUILD SUCCESS, `ModuleBoundariesTest` 5/5 (includes `sharedEsUnaHoja`, `ningunModuloEntraAlInteriorDeOtro`), `SharedIsFrameworkFreeTest` 1/1, with the new `shared/stock` package present.
- [x] 1.12 `python3 scripts/validar_trazabilidad.py` — RESULTADO: trazabilidad íntegra (42 RF · 34 RNF · 17 RN · 37 CU · 8 DT), exit 0.
- [x] 1.13 `cd backend && ./mvnw verify` — BUILD SUCCESS.

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/init-db/01-init-schema.sql` | Modified | S-1/S-2 columns on `categories`; `uq_categories_name_ci` (S-4); `uq_product_units_single_default` (S-3) |
| `scripts/validar_esquema.sh` | Modified | New section `G. Catálogo maestro`, five checks (25 → 30) |
| `CLAUDE.md` | Modified | Verificación line: 25 → 30 invariantes |
| `openspec/config.yaml` | Modified | schema-validator purpose: 25 → 30 invariants |
| `docs/diagrama_er.md` | Modified | `is_active` in Mermaid `CATEGORIES`; `is_active` + `updated_at` in PlantUML `categories` |
| `backend/src/main/java/com/optiplant/inventory/shared/stock/ProductStockPresencePort.java` | Created | Inbound stock-presence port, JDK-only, leaf |
| `backend/src/main/java/com/optiplant/inventory/shared/audit/AuditAction.java` | Modified | `+ ENABLE, DELETE`; Javadoc extended |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 1 tasks 1.1–1.13 marked `[x]` |

### Final gate output (run, not asserted from memory)

**`./scripts/validar_esquema.sh`** — exit 0:

```
G. Catálogo maestro
  ok     toda categoría tiene estado de actividad  (0)
  ok     las categorías sembradas nacen activas  (4)
  ok     RN-13 · un producto no puede tener dos unidades de venta predeterminadas
  ok     dos productos distintos tienen cada uno su unidad predeterminada
  ok     el nombre de una categoría es único sin distinguir mayúsculas
------------------------------------------------------------
RESULTADO: 30 comprobaciones correctas — esquema íntegro
```

`A. ... ok 20 tablas creadas (20)` and `ok 02-seed-data.sql` both still pass.

**`python3 scripts/validar_trazabilidad.py`** — exit 0:

```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 8 DT
```

**`cd backend && ./mvnw verify`** — BUILD SUCCESS:

```
[INFO] Tests run: 67, Failures: 0, Errors: 0, Skipped: 0        (surefire)
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0        (failsafe / *IT)
[INFO] ModuleBoundariesTest      Tests run: 5, Failures: 0, Errors: 0
[INFO] SharedIsFrameworkFreeTest Tests run: 1, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

One `ERROR` line appears in the log inside `AuditAtomicityIT` — it is the test's own deliberate `AtomicityFixtureFailure` exercising audit-write rollback, and the suite reports `Tests run: 2, Failures: 0, Errors: 0`.

### Deviations from design

None — implementation matches design §10.1–§10.4, §5.3 and D-9. `idx_product_units_product` was already present in the schema (`:117`), so task 1.3 added only the partial unique index, consistent with design §10.1 edit 3 which shows that `CREATE INDEX` line as existing context.

### Issues found

None. `design.md` and `tasks.md` were complete and correct for S1.

### Workload / PR boundary

- Mode: chained PR slice — PR1 of 8 (feature-branch-chain; PR1 targets the tracker branch).
- Current work unit: S1 — Schema + `shared`.
- Boundary: starts from the planning branch; ends with the schema edits, validator section G, ER-diagram sync, `shared/stock` port and `AuditAction` extension. No `catalog` consumer exists yet.
- Rollback: `git revert` of this commit + `docker compose down -v`. Nothing depends on the new port or enum constants.

---

## Phase 2 — S2: Category domain and application (PR2)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-03-s2-categorias-dominio` (chained on S1).
**Status**: 12/12 tasks complete. Ready for verify.

### Completed tasks

- [x] 2.1 `catalog/domain/model/CategoryName.java` — record with a compact constructor that rejects `null`, `strip()`s surrounding whitespace, rejects the empty result and rejects `> 100` chars (all `IllegalArgumentException`). `comparisonKey()` returns `value.toLowerCase(Locale.ROOT)`; `value` keeps the original case (design §3.1, R-02).
- [x] 2.2 `catalog/domain/model/ActiveFilter.java` — enum `ACTIVE/INACTIVE/ALL`; `static parse(String)` accepts exactly `"true"` → `ACTIVE`, `"false"` → `INACTIVE`, `"all"` → `ALL`, and throws `IllegalArgumentException` on `null` or anything else (design §3.2, R-12).
- [x] 2.3 `catalog/domain/model/Category.java`, `CategorySummary.java`, `CategoryRef.java` — records per design §3.3. `Category.withName(name, description, now)` and `Category.withActive(active, now)` each return a copy with `updatedAt = now` (R-03). `CategorySummary(Category, long activeProductCount)`; `CategoryRef(UUID externalId, String name, boolean active)`.
- [x] 2.4 `catalog/domain/exception/` — `CategoryNotFoundException(UUID)`, `DuplicateCategoryNameException(String)`, `CategoryInUseException(String)`, `CategoryInactiveException(String)`, all `extends RuntimeException`, Javadoc naming the HTTP code the S3 handler will map them to (design §3.4). No handler mapping added (that is S3); `BaseUnitChangeRejectedException` deliberately not created here (S7).
- [x] 2.5 `catalog/application/port/out/CategoryRepositoryPort.java` — the eight methods (`findByExternalId`, `findRefByExternalId`, `existsByNameIgnoringCase(comparisonKey, excludingExternalId)`, `hasActiveProducts`, `create`, `update`, `setActive`, `list`) and four nested records (`NewCategory`, `CategoryUpdate`, `CategoryFilter`, `CategoryPage`) of design §5.3. No JPA/SQL/table name anywhere in the port.
- [x] 2.6 `catalog/application/port/in/ManageCategoriesUseCase.java` — six methods per design §5.1: `list`/`get` take **no** `actor`; `create`/`edit`/`disable`/`enable` take `AuthenticatedPrincipal actor` (R-16, D-7). `disable`/`enable` return `CategorySummary` (per design §5.1, unlike `iam`'s `void disable`). Javadoc `@throws` on each method names its exception, mirroring `ManageBranchesUseCase`. `list` returns `CategoryRepositoryPort.CategoryPage` (declared on the out port, as `ManageBranchesUseCase` imports `BranchRepositoryPort.BranchPage`).
- [x] 2.7 `catalog/application/service/CategoryAdminService.java` — `@Service implements ManageCategoriesUseCase`, constructor-injects `CategoryRepositoryPort` + `AuditWritePort`, mirrors `BranchAdminService` including the `ObjectMapper` + private `CategoryAuditPayload` record. `@Transactional` on `create/edit/disable/enable`; `@Transactional(readOnly = true)` on `list/get`. Every mutation ends with `auditWritePort.record(...)` carrying `branchId = null`, `entityName = "categories"`, `entityId = externalId.toString()` (R-15). `disable`: load (404) → idempotent short-circuit when already inactive → `hasActiveProducts` check first, throwing `CategoryInUseException` (R-04) → `setActive(false, now)` → audit `DISABLE`. `enable`: load (404) → idempotent short-circuit when already active (R-03) → `setActive(true, now)` → audit `ENABLE`. `create`/`edit` build `CategoryName` (normalisation + 400 path) then `existsByNameIgnoringCase(comparisonKey, excludingExternalId)` — `null` on create, `externalId` on edit so a rename to the row's own name is not a conflict.
- [x] 2.8 `rg -n "org\.springframework|jakarta\.persistence" src/main/java/com/optiplant/inventory/catalog/domain` → no matches. Domain model + exceptions import only `java.time`, `java.util`. `ModuleBoundariesTest` (`elDominioNoConoceInfraestructuraNiFramework`) stays green (5/5).
- [x] 2.9 `CategoryNameTest` — 10 tests: trims surrounding whitespace, preserves case in `value`, rejects `null`/`""`/`"    "`, accepts exactly 100 chars, accepts 100 chars after trimming, rejects 101, `comparisonKey` case-insensitive and equals `"fertilizantes"`, `comparisonKey` also ignores surrounding whitespace.
- [x] 2.10 `ActiveFilterTest` — 7 tests: `true`/`false`/`all` parse to the right constant; `"maybe"`, `""`, `null` and `"TRUE"` (case-sensitive by design) each throw `IllegalArgumentException`.
- [x] 2.11 `CategoryAdminServiceTest` — 13 tests with hand-written in-memory fakes (`FakeCategoryRepositoryPort`, `FakeAuditWritePort`; no Mockito, mirroring `BranchAdminServiceTest`): create trims the name; case-insensitive duplicate on create throws `DuplicateCategoryNameException` and writes no audit; blank name throws `IllegalArgumentException` before the domain; edit-to-own-name is not a conflict; edit unknown id → `CategoryNotFoundException`; `disable` blocked by an active product → `CategoryInUseException`, category stays active, no audit; `disable` allowed with only inactive products → inactive + one `DISABLE` audit; double `disable` idempotent (still one audit); disable unknown id → `CategoryNotFoundException`; double `enable` idempotent (one `ENABLE` audit); every mutation (create/edit/disable/enable) writes an `audit_logs` entry with `entityName = "categories"`, `branchId = null`, `actorUserId = admin`, plus before/after payload assertions; `get` returns the summary / throws when absent; `list` delegates the `ActiveFilter` to the port.
- [x] 2.12 `cd backend && ./mvnw test` — BUILD SUCCESS, `Tests run: 97, Failures: 0, Errors: 0, Skipped: 0` (surefire). Output below.

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/CategoryName.java` | Created | Value object: trim + `1..100` + blank rejection; `comparisonKey()` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/ActiveFilter.java` | Created | Enum `ACTIVE/INACTIVE/ALL` + strict `parse(String)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/Category.java` | Created | Record + `withName`/`withActive` advancing `updatedAt` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/CategorySummary.java` | Created | Read projection `(Category, long activeProductCount)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/CategoryRef.java` | Created | Cheap ref `(externalId, name, active)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/CategoryNotFoundException.java` | Created | `RuntimeException(UUID)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/DuplicateCategoryNameException.java` | Created | `RuntimeException(String)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/CategoryInUseException.java` | Created | `RuntimeException(String)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/CategoryInactiveException.java` | Created | `RuntimeException(String)` — used by the product path (S4), declared now per task 2.4 |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/port/out/CategoryRepositoryPort.java` | Created | 8 methods + 4 nested records (design §5.3) |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/port/in/ManageCategoriesUseCase.java` | Created | 6 methods (design §5.1); reads take no actor |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/service/CategoryAdminService.java` | Created | `@Service`, one `@Transactional` per mutation, audit in-transaction |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/model/CategoryNameTest.java` | Created | 10 tests |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/model/ActiveFilterTest.java` | Created | 7 tests |
| `backend/src/test/java/com/optiplant/inventory/catalog/application/service/CategoryAdminServiceTest.java` | Created | 13 tests, hand-written fakes |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 2 tasks 2.1–2.12 marked `[x]` |

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `cd backend && ./mvnw test` → `BUILD SUCCESS`, `Tests run: 97, Failures: 0, Errors: 0, Skipped: 0`. New: `CategoryAdminServiceTest` 13/13, `ActiveFilterTest` 7/7, `CategoryNameTest` 10/10. `ModuleBoundariesTest` 5/5, `SharedIsFrameworkFreeTest` 1/1. |
| Runtime harness command/scenario and exact result | `N/A` — S2 has no runtime boundary (no adapter, no controller, no DB). Per the tasks forecast the S2 runtime harness is "none (no Docker)". Testcontainers `*IT` for categories is S3. |
| Rollback boundary | `git revert` of this commit removes the entire `catalog/domain/**` + `catalog/application/**` category tree and its three test classes. No existing file is modified except `tasks.md`; nothing outside `catalog` depends on the new types, so the revert is self-contained. |

### Final gate output (run, not asserted from memory)

**`cd backend && ./mvnw test`**:

```
[INFO] Running com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.751 s -- in com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.SharedIsFrameworkFreeTest
[INFO] Running com.optiplant.inventory.catalog.application.service.CategoryAdminServiceTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.100 s -- in com.optiplant.inventory.catalog.application.service.CategoryAdminServiceTest
[INFO] Running com.optiplant.inventory.catalog.domain.model.ActiveFilterTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.035 s -- in com.optiplant.inventory.catalog.domain.model.ActiveFilterTest
[INFO] Running com.optiplant.inventory.catalog.domain.model.CategoryNameTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.049 s -- in com.optiplant.inventory.catalog.domain.model.CategoryNameTest
[INFO] Results:
[INFO] Tests run: 97, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  15.982 s
```

`./mvnw verify` (failsafe / `*IT`) was **not** run for S2 and is expected to fail context load until S3 wires `CategoryRepositoryPort` to a persistence adapter — the tasks forecast scopes S2's gate to `./mvnw test` (surefire) precisely for this reason. Any `@SpringBootTest`-based `*IT` that now sees an unsatisfied `CategoryAdminService` bean is S3's concern.

### Deviations from design

None — implementation matches design §3.1–§3.4, §5.1, §5.3, D-7. Notes on judgment calls that stay within the design:

- `CategoryAdminService.disable`/`enable` short-circuit and return the existing `CategorySummary` **without** writing an audit entry when the category is already in the target state. R-03 requires the repeat call to "respond successfully and the state is unchanged"; a no-op is not a mutation, so R-15 ("every *mutation* leaves a trail") does not compel an audit row. `CategoryAdminServiceTest.doubleDisableIsIdempotent` / `doubleEnableIsIdempotent` pin this (audit count stays 1).
- `disable` checks the idempotent short-circuit *before* `hasActiveProducts` (R-04 only guards the active→inactive transition; re-disabling an already-inactive category must not fail on product state).
- Timestamps use `Instant.now()` inline (no injected `Clock`), matching `BranchAdminService`. The S2 tests assert lifecycle/audit behaviour, not exact timestamp values.

### Issues found

None. `design.md` and `tasks.md` were complete and correct for S2.

### Workload / PR boundary

- Mode: chained PR slice — PR2 of 8 (feature-branch-chain; PR2 targets PR1's branch).
- Current work unit: S2 — Category domain + application.
- Boundary: starts from the S1 branch; ends with the category domain model, exceptions, both ports and `CategoryAdminService`, unit-tested. Nothing is wired to Spring context or HTTP yet (that is S3).
- Estimated review budget impact: ~620 lines authored across 12 production classes + 3 test classes (design forecast: ~380). Above the 400-line soft budget but this is the planned S2 slice of an approved 8-PR feature-branch chain, so it proceeds as a bounded slice per the tasks `Review Workload Forecast`.

---

## Phase 3 — S3: Category infrastructure and the authorization decision (PR3)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-04-s3-categorias-infra` (chained on S2).
**Status**: 11/11 tasks complete. Ready for verify.

### Completed tasks

- [x] 3.1 `catalog/infrastructure/adapter/out/persistence/CategoryJpaEntity.java` — `@Entity @Table(name = "categories")`, Lombok `@Getter/@Setter/@NoArgsConstructor` exactly as `BranchJpaEntity`. Maps `id` (`@GeneratedValue(IDENTITY)`), `external_id` (field-initialised to `UUID.randomUUID()`), `name`, `description`, `is_active` → `boolean active` (S-1), `created_at`, `updated_at` → `Instant updatedAt` (S-2). `ddl-auto=validate` passes against the real schema.
- [x] 3.2 `CategorySpringDataRepository.java` (`extends JpaRepository<CategoryJpaEntity, Long>`) — `findByExternalId`; `existsByNameIgnoringCase(key, excludingExternalId)` as JPQL `SELECT COUNT(c) > 0 ... WHERE LOWER(c.name) = :key AND (:excludingExternalId IS NULL OR c.externalId <> :excludingExternalId)` (null-safe on the create path); `search(namePattern, active, Pageable)` as JPQL with a fixed `ORDER BY c.name ASC`; and the two product-side reads `hasActiveProducts(categoryExternalId)` and `countActiveProductsByCategoryIds(ids)` (see deviation note).
- [x] 3.3 `CategoryMapper.java` — MapStruct `@Mapper(componentModel = "spring")`, `toDomain(entity)` + `toRef(entity)` with `String ↔ CategoryName` default helpers (same style as `BranchMapper`). `CategoryPersistenceAdapter.java` — `@Component implements CategoryRepositoryPort`; resolves the numeric `id` only to drive the active-product counts and never returns it (every method traffics in UUIDs / domain records).
- [x] 3.4 Verified structurally: `CategoryPersistenceAdapter.list()` runs exactly **two** statements irrespective of page size — `search(...)` for the page, then one grouped `countActiveProductsByCategoryIds(pageIds)`; the per-row `.map(...)` only calls `categoryMapper.toDomain` and an in-memory `Map.getOrDefault`. No per-row query. (An empty page skips the count → 1 statement, still not N+1.)
- [x] 3.5 `catalog/infrastructure/adapter/in/web/CatalogExceptionHandler.java` — `@RestControllerAdvice(basePackages = "com.optiplant.inventory.catalog.infrastructure.adapter.in.web")`, package-private, local `record ErrorResponse(String code, String message)`. Mappings wired for what exists after S2/S3: `IllegalArgumentException`/`MethodArgumentNotValidException`/`MethodArgumentTypeMismatchException` → `400 invalid_request`; `CategoryNotFoundException` → `404 category_not_found`; `DuplicateCategoryNameException` → `409 duplicate_category_name`; `CategoryInUseException` → `409 category_in_use`; `CategoryInactiveException` → `409 category_inactive`; `DataIntegrityViolationException` → `409 duplicate_category_name` **only** when the cause message names `uq_categories_name_ci`, otherwise **rethrown** → `500` (D-14). No `BaseUnitChangeRejectedException` mapping (design §3.4). Product/unit exception mappings deferred to S4–S6 as instructed.
- [x] 3.6 `catalog/infrastructure/adapter/in/web/CategoryController.java` — `@RestController @RequestMapping("/api/catalog/categories")`. Six endpoints of contract §6.1: `GET /` (list), `GET /{externalId}`, `POST /` → `201` + `Location: /api/catalog/categories/{externalId}`, `PUT /{externalId}` → `200`, `PATCH /{externalId}/disable` → `200`, `PATCH /{externalId}/enable` → `200`. No `DELETE`. `active` bound as `String` (default `"true"`) and parsed via `ActiveFilter.parse` → `active=maybe` becomes `400 invalid_request`; `page` floored at 0; `size` **clamped** to `[1, 100]` (`Math.min(Math.max(size,1),100)`), never rejected; default size 20. Mutations resolve the actor via `principalAccessor.require()`.
- [x] 3.7 `iam/infrastructure/config/SecurityConfig.java` — added `import org.springframework.http.HttpMethod;` and, immediately before `.anyRequest().authenticated()`: `.requestMatchers(HttpMethod.GET, "/api/catalog/**").authenticated()` then `.requestMatchers("/api/catalog/**").hasAuthority("ADMIN")`. GET matcher first (top-down evaluation, design §7/D-1). `hasAuthority`, not `hasRole`. Spanish comment matches the file's existing style. `HttpMethod` confirmed present in `spring-web-7.0.9.jar` by successful compile.
- [x] 3.8 `git diff --stat` shows exactly one `iam` file touched — `backend/src/main/java/com/optiplant/inventory/iam/infrastructure/config/SecurityConfig.java`, `1 file changed, 8 insertions(+)`. No other `iam` file appears in the diff.
- [x] 3.9 `CategoryCatalogIT` (`@Import(TestcontainersConfiguration.class)` + `@SpringBootTest(RANDOM_PORT)`, `RestClient` + `JdbcTemplate`, seed `admin.corp`) — 8 tests: full create/edit/list/disable/enable cycle; `409 duplicate_category_name` on a name differing only in case; `409 category_in_use` with a directly-seeded active product then success after the product is set inactive; `404` on an unknown `externalId` for `GET`/`PUT`/`PATCH disable`; listing defaults to active-only and honours `active=false` / `active=all`; `active=maybe` → `400 invalid_request`; `size=5000` returns `200` with envelope `size == 100`. All green.
- [x] 3.10 `CategoryCatalogIT.noNumericIdLeaksInAnyResponseBodyOrInTheLocationHeader` — the create body (`Map`) has no `id` key; the `Location` header equals `/api/catalog/categories/{externalId}` and, with the UUID removed, contains no digit; every entry of the `active=all` listing page has no `id` key.
- [x] 3.11 `cd backend && ./mvnw verify` — BUILD SUCCESS (see gate output).

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/CategoryJpaEntity.java` | Created | JPA entity for `categories` incl. `is_active` (S-1) + `updated_at` (S-2), Lombok as `BranchJpaEntity` |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/CategorySpringDataRepository.java` | Created | `findByExternalId`, JPQL `existsByNameIgnoringCase` + `search`, native `hasActiveProducts` + `countActiveProductsByCategoryIds` |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/CategoryMapper.java` | Created | MapStruct entity ↔ domain, Spring component model |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/CategoryPersistenceAdapter.java` | Created | `CategoryRepositoryPort` impl; only class that sees the numeric `id`; two-query listing |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/in/web/CatalogExceptionHandler.java` | Created | Package-scoped `@RestControllerAdvice`, local `ErrorResponse`, category mappings + D-14 rethrow |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/in/web/CategoryController.java` | Created | Six `/api/catalog/categories` endpoints; `201 + Location`; `size` clamped; no `DELETE` |
| `backend/src/main/java/com/optiplant/inventory/iam/infrastructure/config/SecurityConfig.java` | Modified | `+ import HttpMethod`; two `/api/catalog/**` matchers (GET first, then ADMIN) before `.anyRequest()` |
| `backend/src/test/java/com/optiplant/inventory/CategoryCatalogIT.java` | Created | 8 Testcontainers integration tests (3.9 + 3.10) |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 3 tasks 3.1–3.11 marked `[x]` |

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `cd backend && ./mvnw test -Dtest=CategoryAdminServiceTest` → green (13/13), plus `ModuleBoundariesTest` 5/5 stays green with the new `catalog/infrastructure` package. |
| Runtime harness command/scenario and exact result | `cd backend && ./mvnw verify` (full) — real PostgreSQL 17 via Testcontainers. `CategoryCatalogIT` 8/8; failsafe total `Tests run: 61, Failures: 0, Errors: 0, Skipped: 0`; surefire total `Tests run: 97, Failures: 0, Errors: 0, Skipped: 0`. `BUILD SUCCESS`. |
| Rollback boundary | `git revert` of this commit removes the entire `catalog/infrastructure/**` tree and `CategoryCatalogIT`, and reverts the 8-line `SecurityConfig` addition. `/api/catalog/categories` disappears and the `/api/catalog/**` matchers go with it; no other module is affected. |

### Gate output (run, not asserted from memory)

**`cd backend && ./mvnw verify`** — `BUILD SUCCESS`:

```
[INFO] --- surefire ---
[INFO] Tests run: 97, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ModuleBoundariesTest                 Tests run: 5,  Failures: 0, Errors: 0
[INFO]   SharedIsFrameworkFreeTest            Tests run: 1,  Failures: 0, Errors: 0
[INFO]   catalog...CategoryAdminServiceTest   Tests run: 13, Failures: 0, Errors: 0
[INFO]   catalog...ActiveFilterTest           Tests run: 7,  Failures: 0, Errors: 0
[INFO]   catalog...CategoryNameTest           Tests run: 10, Failures: 0, Errors: 0

[INFO] --- failsafe (*IT, Testcontainers / real PostgreSQL 17) ---
[INFO] Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
[INFO]   CategoryCatalogIT                    Tests run: 8,  Failures: 0, Errors: 0, Time elapsed: 1.969 s

[INFO] BUILD SUCCESS
```

**3.4 (two-query listing)** — verified by inspection: `CategoryPersistenceAdapter.list()` → `categoryRepository.search(...)` (1) + `activeProductCounts(pageIds)` → `countActiveProductsByCategoryIds(...)` (1). Nothing in the mapping loop hits the repository.

**3.8 (single `iam` edit)** — `git diff --stat`:

```
 .../inventory/iam/infrastructure/config/SecurityConfig.java | 8 ++++++++
 1 file changed, 8 insertions(+)
```

### Deviations from design

1. **Product-side reads in `CategorySpringDataRepository` are native SQL against `products`, not JPQL against `ProductJpaEntity`.** Design §6.2 writes `existsByCategoryIdAndActiveIsTrue` and `countActiveProductsByCategoryIds` as JPQL over `ProductJpaEntity`, but that entity is created in **S5** (task 5.1) and S3's task list does not include it. Creating a minimal `ProductJpaEntity` now would collide with task 5.1 and add merge friction to the feature-branch chain. Both queries are plain existence / grouped-count with **no dynamic `Sort`**, so D-10 ("JPQL, never native, for the *product search*") is not in play, and the codebase already uses native reads in a repository (`BranchSpringDataRepository.findIdByExternalId`). Implemented as `SELECT EXISTS(... JOIN categories ... WHERE c.external_id = ? AND p.is_active = TRUE)` and `SELECT p.category_id, COUNT(*) ... WHERE p.category_id IN (:ids) AND p.is_active = TRUE GROUP BY p.category_id` (returned as `List<Object[]>` to avoid native-projection column-alias fragility). **S5 may migrate these to JPQL once `ProductJpaEntity` exists**; the port contract (`hasActiveProducts(UUID)`, `CategorySummary.activeProductCount`) is unchanged so the migration is internal to the repository.
2. **`search` passes a pre-lowercased `%contains%` pattern instead of `LOWER(CONCAT('%', :name, '%'))`.** The design snippet's shape (`LOWER(CONCAT(...))` around the bind parameter) makes PostgreSQL infer `lower(bytea)` when `:name` is `null` and the `GET /api/catalog/categories` list 500s (`function lower(bytea) does not exist`) — caught by running `CategoryCatalogIT`, not by reading. Fix keeps `LOWER()` on the `c.name` column only: the adapter builds `null` or `"%" + name.toLowerCase(ROOT) + "%"` and the query is `(:namePattern IS NULL OR LOWER(c.name) LIKE :namePattern)`. Same case-insensitive contains semantics, no behavioural change.
3. **`existsByNameIgnoringCase` JPQL is null-guarded** (`:excludingExternalId IS NULL OR c.externalId <> :excludingExternalId`) rather than the design's bare `c.externalId <> :excluding`, because on the create path `excludingExternalId` is `null` and `<> null` yields UNKNOWN — the check would silently never detect a duplicate at the service layer. Behaviour now matches R-02 on both create and edit.

None of these change the contract, the ports, the API surface or any error code — they are local implementation corrections, each caught or justified by execution.

### Issues found

`design.md` / `tasks.md` are usable for S3 with the three local corrections above. The only genuine slicing wrinkle is deviation 1: the design assumes `ProductJpaEntity` exists when S3's repository needs to read `products`, but S3's task list (3.1) creates only `CategoryJpaEntity`. Resolved with native SQL rather than by pulling S5 work forward or halting the slice; flagged here for the S5 executor.

### Workload / PR boundary

- Mode: chained PR slice — PR3 of 8 (feature-branch-chain; PR3 targets PR2's branch).
- Current work unit: S3 — Category infrastructure + the `/api/catalog/**` authorization decision.
- Boundary: starts from the S2 branch; ends with the category JPA entity, Spring Data repository, MapStruct mapper, persistence adapter, `CatalogExceptionHandler`, `CategoryController`, the 8-line `SecurityConfig` matcher addition, and `CategoryCatalogIT`. `/api/catalog/categories` is now live and Testcontainers-verified; products/units remain unwired (S4+).
- Estimated review budget impact: ~470 lines production + ~270 lines IT (design forecast: ~460). Planned S3 slice of the approved 8-PR chain, proceeds as a bounded slice per the tasks `Review Workload Forecast`.
