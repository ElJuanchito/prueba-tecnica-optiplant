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

---

## Phase 4 — S4: Product domain and application (PR4)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-05-s4-productos-dominio` (chained on S3).
**Status**: 15/15 tasks complete. Ready for verify.

### Completed tasks

- [x] 4.1 `catalog/domain/model/Sku.java` — record; compact constructor rejects `null`, `strip()`s then `toUpperCase(Locale.ROOT)`, rejects the empty result, rejects `> 50` chars (all `IllegalArgumentException`). Every persisted SKU is upper-case, so the existing `UNIQUE (sku)` is a sufficient R-06 guarantee.
- [x] 4.2 `catalog/domain/model/UnitCode.java` — record; canonical constructor normalizes (strip + upper-case) and enforces `^[A-Z0-9_]+$` + `1..50`; `static UnitCode.baseUnit(String)` runs the same `normalize(...)` with the tighter `1..20` bound. Whitespace inside the value is rejected by the charset (`"Saco de 50"` → `"SACO DE 50"` → fails).
- [x] 4.3 `catalog/domain/model/ProductSort.java` — enum `SKU/NAME/CREATED_AT`; `static parse(String)` accepts exactly `"sku"`/`"name"`/`"createdAt"`, throws `IllegalArgumentException` on `null` or anything else, so `sort=(select 1)` becomes `400` and is never interpolated (R-12).
- [x] 4.4 `catalog/domain/model/ProductUnit.java` — record `(externalId, UnitCode unitName, BigDecimal conversionFactor, boolean defaultSaleUnit, Instant createdAt)`; compact constructor throws `InvalidConversionFactorException` when `conversionFactor` is `null` or `signum() <= 0`. `BigDecimal`, never `double`.
- [x] 4.5 `catalog/domain/model/Product.java` — record per design §3.3; compact constructor does `List.copyOf(units)` (null → `List.of()`) then asserts: no duplicate `unitName` (`DuplicateProductUnitException`), no base-unit homonym with `conversionFactor.compareTo(BigDecimal.ONE) != 0` (`InvalidConversionFactorException`), at most one `defaultSaleUnit` (`IllegalStateException`). Added `withDetails` / `withActive` / `withBaseUnit` (advance `updatedAt`) and `withUnits` (re-asserts invariants; leaves `updatedAt` untouched — the product row is unchanged).
- [x] 4.6 `catalog/domain/model/ProductSummary.java` — list projection `(externalId, Sku sku, String name, UnitCode baseUnit, boolean active, CategoryRef category, Instant createdAt, Instant updatedAt)`; deliberately **without** `units` and `description`.
- [x] 4.7 `catalog/domain/exception/` — `ProductNotFoundException(UUID)`, `DuplicateSkuException(String)`, `DuplicateProductUnitException(String)`, `InvalidConversionFactorException(String)`, all extending `RuntimeException`, mirroring the category exceptions' style. **No** `CatalogExceptionHandler` mapping added (that is S5's task 5.8).
- [x] 4.8 `catalog/application/port/out/ProductRepositoryPort.java` — per design §5.3: `findByExternalId`, `existsBySku(normalizedSku, excludingExternalId)`, `create`, `update`, `setActive`, `setBaseUnit` (R-08, used only by S7), `list`; nested records `NewProduct`, `NewUnitRow`, `ProductUpdate`, `ProductFilter`, `ProductPage`.
- [x] 4.9 `catalog/application/port/in/ManageProductsUseCase.java` — per design §5.1: `list`/`get` (no actor), `create`/`edit`/`disable`/`enable`/`changeBaseUnit` (take `AuthenticatedPrincipal actor`). `EditProductCommand` has **no** `baseUnit` field. Javadoc names the exception each method may throw, as `ManageBranchesUseCase` does. `changeBaseUnit` declared here, wired in S7.
- [x] 4.10 `catalog/application/service/ProductAdminService.java` — `@Service` implementing `ManageProductsUseCase`. `@Transactional` on mutations, `@Transactional(readOnly = true)` on `list`/`get`. Each mutation: resolve category ref via `CategoryRepositoryPort.findRefByExternalId` first — missing → `CategoryNotFoundException` (404), inactive → `CategoryInactiveException` (409) (R-05); SKU uniqueness via `productRepository.existsBySku(sku.value(), excludingExternalId)` — `null` on create, `externalId` on edit (R-09); `enable` re-checks `existing.category().active()` (R-11); `create` builds a transient `Product` aggregate to assert R-13/R-14 across inline units before any SQL (design §8.2); every mutation ends with `auditWritePort.record(...)` carrying `entityName = "products"`, `branchId = null`; `disable`/`enable` are idempotent no-ops when already in the target state. `changeBaseUnit` throws `UnsupportedOperationException("changeBaseUnit is delivered in slice S7")` — body **not** guessed.
- [x] 4.11 `SkuTest` (9 tests) — `abc-1` and `ABC-1` yield the same value `ABC-1`; mixed-case upper-cased; trimming; null/empty/whitespace rejected; 50 accepted (also after trimming); 51 rejected.
- [x] 4.12 `UnitCodeTest` (11 tests) — `kg` → `KG`; trimming; `"Saco de 50"` and `"KG-2"` rejected (charset); null/blank rejected; canonical factory 50 ok / 51 rejected; `baseUnit` normalizes like the canonical factory, 20 ok, 21 rejected **while `new UnitCode("A".repeat(21))` succeeds** — the two bounds shown side by side.
- [x] 4.13 `ProductInvariantsTest` (8 tests) — two units of the same name → `DuplicateProductUnitException`; base-unit homonym factor `2` → `InvalidConversionFactorException`, factor `1.0000` accepted; two defaults → `IllegalStateException`, one/zero defaults accepted; unit-level factor `0`/`-1` rejected; unit list copied defensively.
- [x] 4.14 `ProductAdminServiceTest` (14 tests) — hand-written in-memory fakes (`FakeProductRepositoryPort`, `FakeCategoryRepositoryPort`, `FakeAuditWritePort`, no Mockito): `category_not_found` and `category_inactive` on create/edit/enable, `duplicate_sku` (case-insensitive) on create and edit, editing a product to its own SKU is **not** a conflict, unknown product → `ProductNotFoundException`, `disable` idempotent, and every mutation writes an audit entry with `entityName = "products"`, `branchId = null`, the actor id and the product `external_id`, in order `CREATE, UPDATE, DISABLE, ENABLE`.
- [x] 4.15 `cd backend && ./mvnw test` — BUILD SUCCESS.

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/Sku.java` | Created | SKU value object — trim + upper-case, `1..50`, `IllegalArgumentException` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/UnitCode.java` | Created | Unit-code value object — normalize + `^[A-Z0-9_]+$`; `1..50` canonical, `baseUnit(String)` `1..20` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/ProductSort.java` | Created | Closed sort allow-list `SKU/NAME/CREATED_AT` + `parse(String)` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/ProductUnit.java` | Created | Alternative-unit record; compact ctor rejects null / non-positive factor |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/Product.java` | Created | Product aggregate; compact ctor copies units + asserts R-13/R-14; four `with*` copies |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/model/ProductSummary.java` | Created | List projection without `units`/`description` |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/ProductNotFoundException.java` | Created | `RuntimeException`, `UUID` ctor |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/DuplicateSkuException.java` | Created | `RuntimeException`, `String` ctor |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/DuplicateProductUnitException.java` | Created | `RuntimeException`, `String` ctor |
| `backend/src/main/java/com/optiplant/inventory/catalog/domain/exception/InvalidConversionFactorException.java` | Created | `RuntimeException`, `String` ctor |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/port/out/ProductRepositoryPort.java` | Created | Secondary port + `NewProduct`/`NewUnitRow`/`ProductUpdate`/`ProductFilter`/`ProductPage` |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/port/in/ManageProductsUseCase.java` | Created | Primary port; `EditProductCommand` without `baseUnit`; `changeBaseUnit` declared |
| `backend/src/main/java/com/optiplant/inventory/catalog/application/service/ProductAdminService.java` | Created | `@Service`; create/edit/disable/enable + audit; `changeBaseUnit` → `UnsupportedOperationException` |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/model/SkuTest.java` | Created | 9 tests (R-06) |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/model/UnitCodeTest.java` | Created | 11 tests (R-07, R-13) |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/model/ProductInvariantsTest.java` | Created | 8 tests (R-13, R-14) |
| `backend/src/test/java/com/optiplant/inventory/catalog/application/service/ProductAdminServiceTest.java` | Created | 14 tests (R-05, R-06, R-09, R-15) |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 4 tasks 4.1–4.15 marked `[x]` |

### `./mvnw test` output (run, not asserted from memory)

```
[INFO] Running com.optiplant.inventory.catalog.application.service.ProductAdminServiceTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- in ...ProductAdminServiceTest
[INFO] Running com.optiplant.inventory.catalog.domain.model.ProductInvariantsTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ...ProductInvariantsTest
[INFO] Running com.optiplant.inventory.catalog.domain.model.SkuTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0 -- in ...SkuTest
[INFO] Running com.optiplant.inventory.catalog.domain.model.UnitCodeTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- in ...UnitCodeTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in com.optiplant.inventory.SharedIsFrameworkFreeTest
[INFO] Tests run: 139, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Surefire total 139 (was 67 at S1; S2/S3/S4 added the rest), all green. `ModuleBoundariesTest` 5/5 — the new `catalog/domain` product model imports only the JDK (`java.util.*`, `java.time.Instant`, `java.math.BigDecimal`, `java.util.regex.Pattern`) plus `catalog/domain/exception`, so `elDominioNoConoceInfraestructuraNiFramework` and `sharedEsUnaHoja` stay green. `./mvnw verify` (Testcontainers) is deliberately **not** run for S4 — this slice wires no adapter and the tasks `Suggested Work Units` table lists its runtime harness as `none`.

### Deviations from design

1. **`ProductAdminService.create` constructs a throwaway `Product` aggregate purely to assert R-13/R-14 across inline units before calling the port.** Design §8.2 states "`Product`'s compact constructor has already rejected a payload carrying two defaults before any SQL is issued", but the out-port takes primitive `NewProduct`/`NewUnitRow` rows, not a domain `Product`. To make §8.2's claim literally true on the create path, the service builds `List<ProductUnit>` (each validates its own factor) and then `new Product(PLACEHOLDER_ID, sku, name, description, baseUnit, true, category, units, Instant.EPOCH, Instant.EPOCH)` — the id/timestamps are placeholders discarded immediately; only the constructor's invariant assertions matter. The persisted rows are then derived from the validated `ProductUnit` list. No behavioural or contract change; it is the single place the cross-unit invariants are enforced on create, as the design intends.
2. **`enable` re-checks the category via the `CategoryRef` already embedded in the loaded `Product`, not a fresh `CategoryRepositoryPort` call.** `Product` carries `CategoryRef category` (design §3.3) with its `active` flag, loaded in the same transaction by `findByExternalId`, so it reflects current state. This avoids a redundant port round-trip and keeps R-11's "re-enabling under an inactive category → `category_inactive`" satisfied. `create`/`edit` still resolve through the port because they take a *new* `categoryExternalId` that is not yet on any loaded aggregate.

Neither changes the ports, the API surface, or any error code.

### Issues found

None. `design.md` §3.1, §3.3, §3.4, §5.1, §5.3, §6.2 and `tasks.md` Phase 4 were complete and internally consistent for S4. The one genuine friction point (out-port shape vs. §8.2's "the `Product` constructor rejects it" phrasing) is resolved by deviation 1 above without redesigning anything.

### Workload / PR boundary

- Mode: chained PR slice — PR4 of 8 (feature-branch-chain; PR4 targets PR3's branch).
- Current work unit: S4 — Product domain + application.
- Boundary: starts from the S3 branch; ends with the product value objects, aggregate, projections, exceptions, both ports and `ProductAdminService` plus their unit tests. Nothing is wired to HTTP or persistence — `/api/catalog/products` does not exist yet (S5).
- Rollback: `git revert` of this commit; no consumer of the new types exists outside their own tests.
- Estimated review budget impact: ~470 lines production + ~430 lines unit tests (design forecast: ~420). Planned S4 slice of the approved 8-PR chain; proceeds as a bounded slice per the tasks `Review Workload Forecast`.

---

## Phase 5 — S5: Product infrastructure (PR5)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-06-s5-productos-infra` (chained on S4).
**Status**: 13/13 tasks complete. Ready for verify.

### Completed tasks

- [x] 5.1 `catalog/infrastructure/adapter/out/persistence/ProductJpaEntity.java` — `@Entity @Table(name = "products")`, Lombok `@Getter/@Setter/@NoArgsConstructor` as `BranchJpaEntity`. `@ManyToOne(fetch = LAZY, optional = false) @JoinColumn(name = "category_id", nullable = false)` to `CategoryJpaEntity`; `@OneToMany(mappedBy = "product", cascade = ALL, orphanRemoval = true)` to `ProductUnitJpaEntity` with an `addUnit(...)` helper that keeps the back-reference consistent for the cascade. `is_active` → `boolean active`, `base_unit` → `String baseUnit`, `external_id` field-initialised to `UUID.randomUUID()`. `ddl-auto=validate` passes against the real schema.
- [x] 5.2 `ProductUnitJpaEntity.java` — maps `product_units`; `conversion_factor NUMERIC(12,4)` → `BigDecimal conversionFactor`; `is_default_sale_unit` → `boolean defaultSaleUnit`; `@ManyToOne(fetch = LAZY, optional = false)` back to `ProductJpaEntity`. **No `updatedAt` field** — the table has no such column.
- [x] 5.3 `ProductSpringDataRepository.java` (`extends JpaRepository<ProductJpaEntity, Long>`) — `search(...)` is **JPQL** (`@Query` value + explicit `countQuery`) with `JOIN FETCH p.category c`, `:q` contains-match against `LOWER(p.sku)` / `LOWER(p.name)`, `:categoryExternalId` (see deviation 1) and `:active`; the `Pageable` carries the `Sort`. `findByExternalIdWithUnits` is `SELECT DISTINCT p ... JOIN FETCH p.category LEFT JOIN FETCH p.units` (see deviation 2). `existsBySku(normalizedSku, excludingExternalId)` JPQL, null-guarded on the create path exactly as S3's `existsByNameIgnoringCase`.
- [x] 5.4 **[BLOCKING] — passed by execution.** `ProductCatalogIT.pageableSortFromProductSortOrdersAllThreeFieldsInBothDirections` creates three products with `sku`/`name`/`createdAt` deliberately in three *different* orders (20 ms sleeps between creates), then asserts the server-returned order for all six combinations: `sort=sku` asc → `[AAA, MMM, ZZZ]` / desc reversed; `sort=name` asc → `[Aaa, Mmm, Zzz]` / desc reversed; `sort=createdAt` asc → creation order `[1st, 2nd, 3rd]` / desc reversed. All six `assertThat(...).containsExactly(...)` pass against real PostgreSQL 17 — the `Pageable` `Sort` built from `ProductSort` genuinely orders on every field and direction.
- [x] 5.5 `ProductMapper.java` (MapStruct `@Mapper(componentModel = "spring")`, `toDomain` / `toSummary` / `toCategoryRef` / `toUnit` + `String↔Sku` / `String↔UnitCode` default helpers, same style as `CategoryMapper`). `ProductPersistenceAdapter.java` (`@Component implements ProductRepositoryPort`) — `create` builds the `ProductJpaEntity` + inline `ProductUnitJpaEntity` rows and persists them in **one** `save` via the `cascade = ALL` association (R-06); `update`/`setActive`/`setBaseUnit` load-mutate-save; `list` builds `Sort` from the closed `ProductSort` allow-list. Injects `CategorySpringDataRepository` (same infra package) to resolve `category_id` for writes — the adapter is the only class that sees a numeric `id` and never returns one.
- [x] 5.6 `catalog/infrastructure/adapter/in/web/ProductController.java` — `@RestController @RequestMapping("/api/catalog/products")`, six endpoints of contract §6.2. `GET /` list with `q`/`categoryId`/`active`/`sort`/`direction`/`page`/`size`; `active` & `sort` bound as `String` and parsed via `ActiveFilter.parse` / `ProductSort.parse`; `direction` parsed to a `boolean ascending` (`asc`/`desc`, else `IllegalArgumentException`); `size` clamped to `[1,100]`, never rejected; `page` floored at 0. `GET /{externalId}` detail embeds `category` + `units`; list item omits `units` and `description`. `POST /` → `201 + Location: /api/catalog/products/{externalId}`. `PATCH /{externalId}/disable|enable` → `200`. No `DELETE`.
- [x] 5.7 `ProductController.EditProductRequest` declares `String baseUnit` solely to reject it: the controller's `edit` method throws `new IllegalArgumentException("baseUnit cannot be changed through this endpoint")` → `400 invalid_request` **before** calling the use case when `request.baseUnit() != null`. `"baseUnit": null` is indistinguishable from absent and passes through (D-8, contract §12.3 point 3). Proven by IT 5.10.
- [x] 5.8 `CatalogExceptionHandler` extended: `ProductNotFoundException` → `404 product_not_found`; `DuplicateSkuException` → `409 duplicate_sku`; `DuplicateProductUnitException` → `409 duplicate_product_unit`; `InvalidConversionFactorException` → `400 invalid_conversion_factor` (last two reachable through `POST /products` with inline units). `DataIntegrityViolationException` now also maps a message naming `products_sku_key` / `products_sku` → `409 duplicate_sku`; anything still unidentifiable rethrows → `500` (D-14). `MethodArgumentNotValidException` / `MethodArgumentTypeMismatchException` were already wired in S3 and their fully-qualified names (`org.springframework.web.bind.MethodArgumentNotValidException`, `org.springframework.web.method.annotation.MethodArgumentTypeMismatchException`) plus `org.springframework.http.HttpMethod` were re-confirmed present in `~/.m2/.../spring-web/7.0.9/spring-web-7.0.9.jar` via `unzip -l` (design §0).
- [x] 5.9 `ProductCatalogIT` — `fullCreateEditDisableEnableReadCycle` (create with inline units + without, Location header, `get` by `externalId`, edit, disable → `200 active:false`, **`get` still `200` with `active:false` not `404`** (R-10), enable); `duplicateSkuIsRejectedOnCreateAndOnEdit` (case-insensitive, both paths, `409 duplicate_sku`); `categoryInactiveIsRejectedOnCreateAndOnReEnable` (`409 category_inactive` creating under a disabled category, and re-enabling a product whose category was disabled after the product); `unknownCategoryReturns404OnCreate` (`404 category_not_found`).
- [x] 5.10 `ProductCatalogIT.putCarryingABaseUnitFieldReturns400AndChangesNothing` — sends a real JSON body with `"baseUnit": "LITRO"` (dedicated `EditWithBaseUnitBody` record so Jackson actually serialises the field), asserts `400 invalid_request`, then `GET`s and asserts `baseUnit` is still `KG` and the `name` is unchanged.
- [x] 5.11 `ProductCatalogIT.listingRespectsActiveFilterSizeClampAndSortAllowList` (active-only default, `active=false`, `active=all`, `active=maybe` → `400 invalid_request`, `size=5000` → envelope `size == 100`, `sort=(select 1)` → `400 invalid_request` via `ProductSort.parse` before any query) + `searchFindsTheSeededNpkProduct` (`q=npk` returns the seeded `FERT-NPK-151515` product `d0000000-…-0001`).
- [x] 5.12 `ProductCatalogIT.disablingAProductWithStockInTwoBranchesLeavesBranchInventoriesUntouched` — creates a product, inserts `branch_inventories` rows for branches 1 and 2 via `JdbcTemplate` (`current_stock` 25.5000 / 10.0000), `PATCH .../disable` → `200 active:false`, then re-reads both rows and asserts count 2 and both `current_stock` values unchanged (R-10 — disable never touches stock).
- [x] 5.13 `cd backend && ./mvnw verify` — `BUILD SUCCESS` (see gate output).

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/ProductJpaEntity.java` | Created | JPA entity for `products`; `@ManyToOne` LAZY to `CategoryJpaEntity`, `@OneToMany` cascade ALL + orphanRemoval to units; `addUnit` helper |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/ProductUnitJpaEntity.java` | Created | JPA entity for `product_units`; `BigDecimal conversionFactor`; **no `updatedAt`** |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/ProductSpringDataRepository.java` | Created | JPQL `search` (+ explicit `countQuery`), `findByExternalIdWithUnits` (`DISTINCT` + fetch joins), `existsBySku` (null-guarded) |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/ProductMapper.java` | Created | MapStruct entity ↔ domain; `toDomain`/`toSummary`/`toCategoryRef`/`toUnit` + VO helpers |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/ProductPersistenceAdapter.java` | Created | `ProductRepositoryPort` impl; single-save cascade create; `Sort` from `ProductSort`; only class that sees numeric `id` |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/in/web/ProductController.java` | Created | Six `/api/catalog/products` endpoints; `201 + Location`; detail embeds category+units, list item omits units+description; `size` clamped; `PUT` rejects `baseUnit` |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/in/web/CatalogExceptionHandler.java` | Modified | + `ProductNotFoundException`/`DuplicateSkuException`/`DuplicateProductUnitException`/`InvalidConversionFactorException` mappings; `products_sku_key` branch on `DataIntegrityViolationException` |
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/out/persistence/CategorySpringDataRepository.java` | Modified | S3 carry-forward: the two product-side reads (`hasActiveProducts`, `countActiveProductsByCategoryIds`) migrated from native SQL to JPQL over `ProductJpaEntity` (now that it exists), matching design §6.2 verbatim; class Javadoc updated |
| `backend/src/test/java/com/optiplant/inventory/ProductCatalogIT.java` | Created | 9 Testcontainers integration tests (5.4, 5.9–5.12) |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 5 tasks 5.1–5.13 marked `[x]` |

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `cd backend && ./mvnw test -Dtest=ProductAdminServiceTest` — green (14/14); ran inside the full `verify` (surefire total `Tests run: 139, Failures: 0, Errors: 0, Skipped: 0` — unchanged from S4 because S5 adds no `*Test`, only a `*IT`). `ModuleBoundariesTest` 5/5, `SharedIsFrameworkFreeTest` 1/1 stay green with the new `catalog/infrastructure` product classes. |
| Runtime harness command/scenario and exact result | `cd backend && ./mvnw verify` — real PostgreSQL 17 via Testcontainers. `ProductCatalogIT` **9/9**; `CategoryCatalogIT` **8/8** (unaffected by the native→JPQL migration); failsafe total `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0` (was 61 at S3 → +9 = exactly `ProductCatalogIT`). `BUILD SUCCESS`, total 01:01 min. |
| Rollback boundary | `git revert` of this commit removes the entire product persistence + web tree (`ProductJpaEntity`, `ProductUnitJpaEntity`, `ProductSpringDataRepository`, `ProductMapper`, `ProductPersistenceAdapter`, `ProductController`), reverts the four new `CatalogExceptionHandler` handlers + the `products_sku_key` branch, reverts `CategorySpringDataRepository` to its S3 native reads, and deletes `ProductCatalogIT`. `/api/catalog/products` disappears; `/api/catalog/categories` and every `iam` route are untouched. |

### Gate output (run, not asserted from memory)

**`cd backend && ./mvnw verify`** — `BUILD SUCCESS`:

```
[INFO] --- surefire ---
[INFO] Tests run: 139, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ModuleBoundariesTest                    Tests run: 5,  Failures: 0, Errors: 0
[INFO]   SharedIsFrameworkFreeTest               Tests run: 1,  Failures: 0, Errors: 0
[INFO]   catalog...ProductAdminServiceTest       Tests run: 14, Failures: 0, Errors: 0
[INFO]   catalog...CategoryAdminServiceTest      Tests run: 13, Failures: 0, Errors: 0
[INFO]   catalog...ProductInvariantsTest         Tests run: 8,  Failures: 0, Errors: 0
[INFO]   catalog...SkuTest                       Tests run: 9,  Failures: 0, Errors: 0
[INFO]   catalog...UnitCodeTest                  Tests run: 11, Failures: 0, Errors: 0
[INFO]   catalog...ActiveFilterTest              Tests run: 7,  Failures: 0, Errors: 0
[INFO]   catalog...CategoryNameTest              Tests run: 10, Failures: 0, Errors: 0

[INFO] --- failsafe (*IT, Testcontainers / real PostgreSQL 17) ---
[INFO] Tests run: 70, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ProductCatalogIT                        Tests run: 9,  Failures: 0, Errors: 0, Time elapsed: 2.621 s
[INFO]   CategoryCatalogIT                       Tests run: 8,  Failures: 0, Errors: 0, Time elapsed: 1.548 s

[INFO] BUILD SUCCESS
[INFO] Total time:  01:01 min
```

The single `ERROR`-level stack trace in the log is `AuditAtomicityIT`'s own deliberate `AtomicityFixtureFailure` (audit-write rollback fixture); that suite still reports `Tests run: 2, Failures: 0, Errors: 0` — same benign line noted in the S1 progress.

**5.4 blocking verification — real ordering, all three fields × both directions:**

| `sort` | `direction=asc` result | `direction=desc` result |
|---|---|---|
| `sku` | `[AAA-SORT, MMM-SORT, ZZZ-SORT]` (2nd, 1st, 3rd created) | reversed |
| `name` | `[Prod Aaa, Prod Mmm, Prod Zzz]` (3rd, 2nd, 1st created) | reversed |
| `createdAt` | creation order `[1st, 2nd, 3rd]` | reversed |

All six `containsExactly` assertions passed against real PostgreSQL 17.

### S3 native-read migration — done

The S3 apply-progress flagged that `CategorySpringDataRepository.hasActiveProducts` / `countActiveProductsByCategoryIds` were native SQL only because `ProductJpaEntity` did not exist yet, and that S5 *may* migrate them. **Migrated.** Both are now JPQL over `ProductJpaEntity` (`p.category.externalId = :categoryExternalId AND p.active = TRUE`; `SELECT p.category.id, COUNT(p) ... GROUP BY p.category.id`), which is exactly what design §6.2 always specified. `CategoryCatalogIT` (8/8) was run as part of `./mvnw verify` and stays green, so the migration is behaviour-preserving. The port contract (`hasActiveProducts(UUID)`, `CategorySummary.activeProductCount`) is unchanged. S3 deviation 1 is thereby retired.

### Deviations from design

1. **Product search filters by `c.externalId = :categoryExternalId`, not the design snippet's `c.id = :categoryId`.** Design §6.2 writes the category-filter clause as `c.id = :categoryId`, implying the adapter resolves the UUID → numeric id first. Filtering on `c.externalId` directly is behaviourally identical, keeps `ProductPersistenceAdapter` free of category numeric-id plumbing for the *read* path (it still resolves `category_id` for *writes*, where the `@ManyToOne` needs a managed entity), and matches the "no numeric id crosses a boundary" spirit. No contract, port, API-surface or error-code change.
2. **`:q` is a pre-lowercased `%contains%` pattern passed as a bind parameter, not `LOWER(CONCAT('%', :q, '%'))` inline.** Identical correction to S3 deviation 2: wrapping a nullable bind parameter in `LOWER(CONCAT(...))` makes PostgreSQL infer `lower(bytea)` and 500 when the parameter is `null`. The adapter builds `null` or `"%" + q.toLowerCase(ROOT) + "%"` and the JPQL keeps `LOWER()` on the `p.sku` / `p.name` columns only. Same case-insensitive contains semantics that task 5.3 asks for ("contains-match against `LOWER(sku)`/`LOWER(name)`"). The load-bearing rule — **JPQL, never native** (D-10) — is honoured.
3. **`findByExternalIdWithUnits` uses `SELECT DISTINCT p` and an explicit `countQuery` on `search`.** `DISTINCT` is required so a fetch-joined `@OneToMany` (units) does not multiply the root rows and break the `Optional<>` single-result contract; the explicit `countQuery` (without the `JOIN FETCH`) keeps pagination's count valid. Both are standard Spring Data JPA idioms, not design departures.
4. **`IllegalStateException` from `Product`'s "> 1 default sale unit" invariant is left unmapped in `CatalogExceptionHandler`.** Design §6.3's mapping table does not list it and instructs against adding mappings beyond the table; validating a two-defaults inline-units payload with a clean code belongs to the units subresource work (S6). Reachable today only via `POST /products` with a hand-crafted double-default `units` array; it would currently surface as `500`. Noted rather than silently "fixed".

### Issues found

None that block S5. `design.md` §6.1–§6.3 and `tasks.md` Phase 5 were complete and internally consistent. The only real friction (design's `c.id = :categoryId` assuming an id-resolution step the read adapter would rather not carry, and the `LOWER(CONCAT(...))` null-inference trap already known from S3) is handled by deviations 1–2 without redesigning anything.

### Workload / PR boundary

- Mode: chained PR slice — PR5 of 8 (feature-branch-chain; PR5 targets PR4's branch).
- Current work unit: S5 — Product infrastructure.
- Boundary: starts from the S4 branch; ends with the two product JPA entities, the Spring Data repository, MapStruct mapper, persistence adapter, `ProductController`, the four new `CatalogExceptionHandler` mappings, the S3 native→JPQL migration, and `ProductCatalogIT`. `/api/catalog/products` is now live and Testcontainers-verified; the units subresource and the base-unit rule remain unwired (S6, S7).
- Estimated review budget impact: ~430 lines production + ~330 lines IT (design forecast: ~430). Planned S5 slice of the approved 8-PR chain; proceeds as a bounded slice per the tasks `Review Workload Forecast`.

---

## Phase 6 — S6: Units of measure per product (PR6)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-07-s6-unidades` (chained on S5).
**Status**: 14/14 tasks complete. `./mvnw verify` green. Ready for verify.

### Completed tasks

- [x] 6.1 `catalog/domain/service/ProductUnitPolicy.java` — pure static functions `addUnit(Product, ProductUnit)`, `replaceUnit(Product, UUID, UnitCode, BigDecimal, boolean)`, `removeUnit(Product, UUID)`, each returning a new `Product`. When the incoming unit is a default, the flag is cleared on every sibling (`replaceAll` / per-element copy) before the new one is applied, so `Product`'s compact constructor never sees two defaults. Base-unit homonym rule (factor 1 only) and duplicate-name rule are re-asserted by `Product.withUnits`. `removeUnit`/`replaceUnit` throw `ProductUnitNotFoundException` when the id is not among `product.units()` — which also covers "belongs to another product", since the aggregate only carries its own units. Framework-free (imports: `catalog.domain.*`, `java.*`).
- [x] 6.2 `catalog/application/port/out/ProductUnitRepositoryPort.java` — `findByProduct`, `find(productExternalId, unitExternalId)` (scoped — a unit of another product resolves to `Optional.empty()`), `clearDefaultSaleUnit(productExternalId)`, `add`, `replace`, `delete`, plus nested `record NewUnitRow(String, BigDecimal, boolean)` (own copy, matching the per-port style of `ProductRepositoryPort`/`CategoryRepositoryPort`). Javadoc pins the load-bearing ordering of design §8.2.
- [x] 6.3 `catalog/application/port/in/ManageProductUnitsUseCase.java` (`list`/`add`/`replace`/`delete`, mutations take `AuthenticatedPrincipal actor`, reads do not — D-7; nested `record UnitCommand`) and `catalog/application/service/ProductUnitAdminService.java` — one `@Transactional` per mutation, `@Transactional(readOnly = true)` on `list`; each mutation loads the product (`ProductNotFoundException` if absent), applies `ProductUnitPolicy` to assert R-13/R-14 **before any SQL**, persists through the port, and ends with `auditWritePort.record(...)` carrying `entityName = "product_units"`, `branchId = null`, `AuditAction.CREATE|UPDATE|DELETE`. Same throwaway-aggregate validation pattern as `ProductAdminService.create`.
- [x] 6.4 `ProductUnitSpringDataRepository.java` — `clearDefaultSaleUnit` is `@Modifying(flushAutomatically = true, clearAutomatically = true)` bulk JPQL `UPDATE ProductUnitJpaEntity u SET u.defaultSaleUnit = FALSE WHERE u.product.externalId = :productExternalId AND u.defaultSaleUnit = TRUE`. Also `findByProductExternalId` (ordered `createdAt, id`), `findScoped(productExternalId, unitExternalId)`, and `deleteScoped` (bulk `@Modifying(flush+clear)` — see deviation 1).
- [x] 6.5 `ProductUnitPersistenceAdapter.java` — `add` and `replace` run design §8.2's two steps in order: **(1)** `clearDefaultSaleUnit(productExternalId)` (flushed `@Modifying`) **only when the incoming unit is a default**, **(2)** then insert / update the row that ends `is_default_sale_unit = TRUE`. `add` clears *before* loading the product so it comes back managed (clearAutomatically detaches everything). `replace` loads the unit first for the not-found guard, then clears, then re-loads, then sets. Skipped entirely for `defaultSaleUnit = false`. Proven by 6.9/6.10 against real PostgreSQL.
- [x] 6.6 `ProductPersistenceAdapter.create` gained a method Javadoc stating **no clearing step is needed** on the inline-units path: the product is brand new so no sibling can hold the flag, and `Product`'s compact constructor already rejected a two-default payload in `ProductAdminService.create` before any SQL. The `uq_product_units_single_default` partial index never sees an intermediate two-`TRUE` state on that path.
- [x] 6.7 `catalog/infrastructure/adapter/in/web/ProductUnitController.java` — `GET` (list, **not paginated** — returns a bare JSON array), `POST` (`201 + Location` carrying `external_id` values only), `PUT /{unitExternalId}`, `DELETE /{unitExternalId}` (`@ResponseStatus(NO_CONTENT)` → `204`). Class-level `@RequestMapping("/api/catalog/products/{productExternalId}/units")`. Authorization is the existing `SecurityConfig` `/api/catalog/**` matchers (GET → authenticated, mutations → `hasAuthority("ADMIN")`); no `SecurityConfig` edit needed.
- [x] 6.8 `CatalogExceptionHandler` extended: `ProductUnitNotFoundException` → `404 product_unit_not_found`; `DataIntegrityViolationException` gains a `uq_product_units_single_default` branch → `409 duplicate_product_unit` ("a product may have only one default sale unit") and a `uq_product_unit` branch → `409 duplicate_product_unit` ("this unit is already defined for the product") — hand-written messages, no constraint name (§7.1 point 2), code per design §6.3. `DuplicateProductUnitException` → `409` was already mapped in S5, unchanged. Plus the S5-hole fix (see below).
- [x] 6.9 **[BLOCKING]** `ProductUnitCatalogIT.replacingTheDefaultSaleUnitCommits` — `@Order(1)`, real PostgreSQL 17. `PUT /api/catalog/products/d0000000-…-001/units/10000000-…-002` (BULTITO_10KG) with `defaultSaleUnit: true`. Asserts all three: **(a)** `200 OK` — committed, no abort on `uq_product_units_single_default`; **(b)** `SELECT count(*) FROM product_units WHERE product_id = 1 AND is_default_sale_unit` → `1`; **(c)** `SELECT unit_name … WHERE product_id = 1 AND is_default_sale_unit` → `BULTITO_10KG`. Green.
- [x] 6.10 `ProductUnitCatalogIT.swappingTheDefaultBackToTheOriginalAlsoCommits` — `@Order(2)`, swaps `SACO_50KG` back to default and asserts the same three (count `1`, surviving row `SACO_50KG`). Restores the seeded state for the rest of `./mvnw verify`. Green.
- [x] 6.11 `catalog/domain/service/ProductUnitPolicyTest.java` — 8 tests: factor `0`/`-1` rejected on `replaceUnit`; base-unit homonym factor ≠ 1 rejected, factor 1 accepted; marking a new default via `addUnit` and via `replaceUnit` leaves exactly one; removing the current default leaves none; duplicate `unitName` rejected; unknown id on `removeUnit`/`replaceUnit` → `ProductUnitNotFoundException`. No Docker.
- [x] 6.12 `ProductUnitCatalogIT` — `twoDifferentProductsCanEachMarkTheirOwnDefault` (both `201`, each `product_id` ends with exactly one default), `aProductWithNoDefaultUnitReadsBackFine` (add a non-default unit, `GET` → `200`, none default), `aDirectSecondDefaultWriteSurfacesAsAConflictNotAServerError` (raw `jdbcTemplate` `INSERT … is_default_sale_unit = TRUE` on a product that already has a default → `DataIntegrityViolationException`, i.e. a conflict, not a `500`).
- [x] 6.13 `ProductUnitCatalogIT` — `deletingAUnitAffectsNoBalanceAndLeavesSiblingsUntouched` (insert a `branch_inventories` row, `DELETE` one of two units → `204`, sibling and the balance row both intact), `deletingTheCurrentDefaultLeavesTheProductWithNone` (`DELETE` the default → `204`, list empty, default count `0`), `aUnitIdBelongingToAnotherProductReturns404` (`DELETE` and `PUT` a unit of product A under product B → `404 product_unit_not_found`).
- [x] 6.14 `cd backend && ./mvnw verify` — BUILD SUCCESS (see gate output).

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `catalog/domain/exception/ProductUnitNotFoundException.java` | Created | `RuntimeException`, ctor takes `UUID`; design §3.4 (covers "hangs off another product") |
| `catalog/domain/service/ProductUnitPolicy.java` | Created | Pure `addUnit`/`replaceUnit`/`removeUnit`; clears sibling defaults before applying a new one (R-13, R-14) |
| `catalog/application/port/out/ProductUnitRepositoryPort.java` | Created | Secondary port + nested `NewUnitRow`; scoped `find`, `clearDefaultSaleUnit`, `add`/`replace`/`delete` |
| `catalog/application/port/in/ManageProductUnitsUseCase.java` | Created | Primary port + nested `UnitCommand`; mutations take `actor`, reads do not |
| `catalog/application/service/ProductUnitAdminService.java` | Created | `@Transactional` per mutation; policy-validate → persist → audit (`product_units`, `branchId = null`) |
| `catalog/infrastructure/adapter/out/persistence/ProductUnitSpringDataRepository.java` | Created | `clearDefaultSaleUnit` (flush+clear bulk UPDATE), `deleteScoped` (flush+clear bulk DELETE), `findScoped`, `findByProductExternalId` |
| `catalog/infrastructure/adapter/out/persistence/ProductUnitPersistenceAdapter.java` | Created | The design §8.2 write sequence: clear-then-set on `add`/`replace`, skipped for non-defaults |
| `catalog/infrastructure/adapter/in/web/ProductUnitController.java` | Created | Four endpoints of contract §6.3; unpaginated list, `201 + Location`, `DELETE` → `204` |
| `catalog/infrastructure/adapter/in/web/CatalogExceptionHandler.java` | Modified | `+ ProductUnitNotFoundException` → `404`; `+ IllegalStateException` (doble-default) → `400`; `+ uq_product_units_single_default` / `uq_product_unit` branches on `DataIntegrityViolationException` → `409` |
| `catalog/infrastructure/adapter/out/persistence/ProductPersistenceAdapter.java` | Modified | `create` Javadoc — why the inline-units path needs no clearing step (task 6.6) |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/service/ProductUnitPolicyTest.java` | Created | 8 unit tests (R-13, R-14), no Docker |
| `backend/src/test/java/com/optiplant/inventory/ProductUnitCatalogIT.java` | Created | 9 IT tests incl. the blocking 6.9/6.10 swap pair and the S5-hole doble-default assertion |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 6 tasks 6.1–6.14 marked `[x]` |

### How the S5 doble-default inline hole was closed

S5 left `POST /api/catalog/products` with two inline `defaultSaleUnit: true` units returning `500`: `Product`'s compact constructor throws `IllegalStateException` for R-14 and nothing mapped it. Design §3.3 fixes that exception type and `ProductInvariantsTest` asserts it, so the type was **not** changed. Instead `CatalogExceptionHandler` gained `@ExceptionHandler(IllegalStateException.class)` mapping it to **`400 invalid_request`**, scoped by a message match (`contains("default sale unit")`) so a genuine server-fault `IllegalStateException` (e.g. the audit-payload serialization wrapper) still falls through to `500`.

**Why 400 and not 409:** design §6.3 routes every malformed *inline-units* payload caught in that same constructor — `IllegalArgumentException`, `InvalidConversionFactorException` — to `400`, and the two-defaults case is caught pre-SQL with no persisted state to conflict with. `409` is reserved for the database rejecting a genuine concurrent/persisted conflict (`uq_product_units_single_default`, task 6.8's `DataIntegrityViolationException` branch). `ProductUnitCatalogIT.postingAProductWithTwoInlineDefaultUnitsIs4xxNot500` sends the payload and asserts `400 invalid_request` + nothing persisted.

### Task 6.6 confirmation

`ProductPersistenceAdapter.create` was reviewed and left functionally unchanged — it already needs no clearing step. The reason is now in its method Javadoc: a brand-new product has no sibling units, and `ProductAdminService.create` constructs the `Product` aggregate (which asserts R-14) before calling the adapter, so `uq_product_units_single_default` never sees two `TRUE` rows on the inline path. Only `ProductUnitPersistenceAdapter.add`/`replace` — where a pre-existing default can be present — run the clear-then-set sequence.

### Deviations from design

1. **Unit deletion uses a bulk `@Modifying(flushAutomatically = true, clearAutomatically = true)` `deleteScoped` JPQL, not `repository.delete(entity)`.** Found by executing: the first `./mvnw verify` failed 6.13 with the deleted unit still present. The service loads the parent `Product` aggregate (with its managed `@OneToMany(cascade = ALL, orphanRemoval = true)` units collection) to drive `ProductUnitPolicy`; an `em.remove` on a child still reachable from that managed collection is silently reconciled away at flush (a well-known JPA gotcha). A bulk `DELETE` bypasses the collection and hits the DB directly, and `clearAutomatically` drops the now-stale collection. This is the same bulk-statement shape design §8.2/D-11 already mandates for `clearDefaultSaleUnit`, so it is consistent with the design's own persistence strategy rather than a departure from its intent. Existence is still validated by the service via `ProductUnitRepositoryPort.find` before the delete, so the `404` semantics are unaffected.
2. **`ProductUnitRepositoryPort` declares its own nested `NewUnitRow`** rather than reusing `ProductRepositoryPort.NewUnitRow` (identical shape). Matches the existing per-port convention (`ProductRepositoryPort`, `CategoryRepositoryPort` each declare all their own nested records) and avoids coupling two out-ports. Design §5.3 uses the bare name `NewUnitRow`; the name is preserved.
3. **`add` clears the previous default *before* loading the product entity** (design §8.2 lists "clear" as step 1 and "set" as step 2 without pinning where the product load sits). Loading after the `clearAutomatically` bulk update guarantees the `ProductJpaEntity` comes back managed, avoiding a detached-association `save`. Ordering of the two SQL statements that matter (clear `SET FALSE` before insert `... TRUE`) is unchanged.

### Issues found

None that block S6. `design.md` §4.1, §5.1, §5.3, §6.2, §6.3, §8.1, §8.2 and `tasks.md` Phase 6 were complete and internally consistent for this slice. The only real friction (the managed-collection delete gotcha) was caught by `./mvnw verify` and resolved with a bulk statement that matches the design's own §8.2 pattern — no redesign.

### Final gate output (run, not asserted from memory)

**`cd backend && ./mvnw verify`** — BUILD SUCCESS, total time 01:38 min.

Surefire (`./mvnw test`, no Docker):

```
[INFO] Running com.optiplant.inventory.catalog.domain.service.ProductUnitPolicyTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ProductUnitPolicyTest
[INFO] Running com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in ModuleBoundariesTest
...
[INFO] Results:
[INFO] Tests run: 147, Failures: 0, Errors: 0, Skipped: 0
```

Failsafe (`*IT`, Testcontainers / real PostgreSQL 17):

```
[INFO] Running com.optiplant.inventory.ProductUnitCatalogIT
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.311 s -- in ProductUnitCatalogIT
[INFO] Running com.optiplant.inventory.CategoryCatalogIT   → Tests run: 8, Failures: 0
[INFO] Running com.optiplant.inventory.ProductCatalogIT     → Tests run: 9, Failures: 0
...
[INFO] Results:
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Blocking task 6.9 — `ProductUnitCatalogIT.replacingTheDefaultSaleUnitCommits`, the three assertions against real PostgreSQL 17:**

| Assertion | Result |
|---|---|
| (a) `PUT …/units/10000000-…-002` (BULTITO_10KG) `defaultSaleUnit: true` → HTTP status | `200 OK` — transaction **committed**, no abort on `uq_product_units_single_default` |
| (b) `SELECT count(*) FROM product_units WHERE product_id = 1 AND is_default_sale_unit` | `1` |
| (c) `SELECT unit_name FROM product_units WHERE product_id = 1 AND is_default_sale_unit` | `BULTITO_10KG` |

6.10 then swaps `SACO_50KG` back to default (`200`, count `1`, surviving row `SACO_50KG`) — the sequence holds in both directions, and the seeded product-1 state is restored for the rest of the suite.

### Workload / PR boundary

- Mode: chained PR slice — PR6 of 8 (feature-branch-chain; PR6 targets PR5's branch `feat/ep-02-catalog-06-s5-productos-infra`).
- Current work unit: S6 — Units of measure per product.
- Boundary: starts from the S5 branch; ends with `ProductUnitPolicy`, the two unit ports, `ProductUnitAdminService`, `ProductUnitSpringDataRepository`, `ProductUnitPersistenceAdapter`, `ProductUnitController`, the three `CatalogExceptionHandler` additions (incl. the S5 doble-default hole), `ProductPersistenceAdapter.create`'s Javadoc, `ProductUnitPolicyTest` and `ProductUnitCatalogIT`. `/api/catalog/products/{id}/units` is now live and Testcontainers-verified. The base-unit rule (S7) and cross-cutting verification (S8) remain.
- Estimated review budget impact: ~470 lines production + ~290 lines tests (design forecast: ~430). Planned S6 slice of the approved 8-PR chain.

---

## Phase 7 — S7: The base-unit rule, without its endpoint (PR7)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-08-s7-regla-unidad-base` (chained on S6).
**Status**: 8/8 tasks complete. `./mvnw verify` green. Ready for verify.

### Completed tasks

- [x] 7.1 `catalog/domain/model/StockPresence.java` — enum `UNTOUCHED / HAS_HISTORY / UNKNOWN` (design §3.2, D-3). Javadoc pins that `UNKNOWN` is the fail-closed "the port could not answer" case (the state of this change) and that `BaseUnitChangePolicy` consumes the enum directly so no `Optional` reaches the domain.
- [x] 7.2 `catalog/domain/exception/BaseUnitChangeRejectedException.java` — `extends RuntimeException`, carries a nested `enum Reason { HAS_HISTORY, PRECONDITION_UNVERIFIABLE }` and a `reason()` accessor; message derived per reason via a `switch`. Javadoc states, in line with design §3.4 / contract §7, that both reasons exist now so the deferred slice (DT-07) can emit two distinct codes without reopening the domain, and that the exception deliberately has **no** `CatalogExceptionHandler` mapping.
- [x] 7.3 `catalog/domain/service/BaseUnitChangePolicy.java` — `public final class`, private ctor, `static Product apply(Product product, UnitCode newBaseUnit, StockPresence presence, Instant now)` (same shape as `ProductUnitPolicy`). `UNTOUCHED` → `product.withBaseUnit(newBaseUnit, now)`; `HAS_HISTORY` → `throw new BaseUnitChangeRejectedException(Reason.HAS_HISTORY)`; `UNKNOWN` → `throw new BaseUnitChangeRejectedException(Reason.PRECONDITION_UNVERIFIABLE)`. Imports only `catalog.domain.*` and `java.time.Instant` — framework-free; takes the enum, never the port (design §4.2, D-3). `ModuleBoundariesTest` 5/5 stays green.
- [x] 7.4 `ProductAdminService.changeBaseUnit` wired. Constructor now takes a 4th arg `Optional<ProductStockPresencePort> stockPresencePort` (Spring injects `Optional.empty()` with no bean — the state of this change). New private `presenceOf(UUID)` maps the bean exactly per design §5.2: `stockPresencePort.map(port -> port.isProductUntouched(id) ? UNTOUCHED : HAS_HISTORY).orElse(StockPresence.UNKNOWN)`. `changeBaseUnit` is `@Transactional`: load product (`ProductNotFoundException` if absent) → `UnitCode.baseUnit(newBaseUnit)` (R-07 normalization) → `BaseUnitChangePolicy.apply(existing, baseUnit, presenceOf(externalId), now)` (throws on refusal) → `productRepository.setBaseUnit(externalId, baseUnit.value(), now)` → `auditWritePort.record(... AuditAction.UPDATE, "products", branchId = null, before/after payloads ...)`. Port call + policy + `setBaseUnit` + audit are in one transaction (contract §8, design §8.1). A refusal throws before any write, so nothing is persisted and no audit row is written. The class Javadoc was updated (removed the "throws `UnsupportedOperationException` until S7" note).
- [x] 7.5 No endpoint, route matcher, error code or `CatalogExceptionHandler` mapping added. Verified: `SecurityConfig` still has exactly the two S3 `/api/catalog/**` matchers; `CatalogExceptionHandler`'s `@ExceptionHandler` list is unchanged and its deliberate "`BaseUnitChangeRejectedException` gets no mapping" Javadoc note remains; `ProductController` has no base-unit route (only the S5 `PUT` field-rejection). See deviation note below on the literal `rg 'base-unit'` wording.
- [x] 7.6 `catalog/domain/service/BaseUnitChangePolicyTest.java` — 3 tests, pure domain, no stub, no Docker: `UNTOUCHED` applies (`baseUnit` → `LITRO`, `updatedAt` → the passed `now`, `externalId`/`sku`/`createdAt` unchanged); `HAS_HISTORY` throws `BaseUnitChangeRejectedException` with `reason() == HAS_HISTORY` and the input product's `baseUnit`/`updatedAt` are untouched; `UNKNOWN` throws with `reason() == PRECONDITION_UNVERIFIABLE`, input untouched (R-08).
- [x] 7.7 `ProductAdminServiceTest` extended (14 → 18 tests). New `FakeProductRepositoryPort.setBaseUnit` now mutates via `withBaseUnit` (was `throw new UnsupportedOperationException("wired in S7")`). New helper `withStockPresencePort(port)` rebuilds the service with `Optional.of(port)`; `setUp` now builds it with `Optional.empty()` (the state of this change). New tests:
  - `changeBaseUnitAppliesWhenThePortReportsTheProductUntouched` — stub returns `true`: `baseUnit` → `LITRO`, `updatedAt` advances past the seeded past instant, one `UPDATE` audit with `entityName = "products"`, `branchId = null`, the product `external_id`.
  - `changeBaseUnitIsRefusedWhenThePortReportsHistoryAndChangesNothing` — stub returns `false`: `BaseUnitChangeRejectedException` / `HAS_HISTORY`; re-read product still `KG`, `updatedAt` unchanged, **no audit**.
  - `changeBaseUnitFailsClosedWhenNoStockPresencePortImplementationIsAvailable` — `Optional.empty()` (the state of this change): `BaseUnitChangeRejectedException` / `PRECONDITION_UNVERIFIABLE`; product unchanged, no audit.
  - `changeBaseUnitRejectsAnUnknownProduct` — `ProductNotFoundException`, no audit.
- [x] 7.8 `cd backend && ./mvnw verify` — `BUILD SUCCESS` (gate output below).

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `catalog/domain/model/StockPresence.java` | Created | Enum `UNTOUCHED/HAS_HISTORY/UNKNOWN` (design §3.2, D-3) |
| `catalog/domain/exception/BaseUnitChangeRejectedException.java` | Created | `RuntimeException` + nested `Reason { HAS_HISTORY, PRECONDITION_UNVERIFIABLE }` + `reason()`; no handler mapping (design §3.4) |
| `catalog/domain/service/BaseUnitChangePolicy.java` | Created | `static apply(product, newBaseUnit, presence, now)`; enum-driven `switch`, framework-free (design §4.2, D-3) |
| `catalog/application/service/ProductAdminService.java` | Modified | Constructor `+ Optional<ProductStockPresencePort>`; `changeBaseUnit` body wired (`@Transactional`: load → policy → `setBaseUnit` → audit, one tx); new private `presenceOf`; class Javadoc updated |
| `backend/src/test/java/com/optiplant/inventory/catalog/domain/service/BaseUnitChangePolicyTest.java` | Created | 3 pure-domain tests (R-08) |
| `backend/src/test/java/com/optiplant/inventory/catalog/application/service/ProductAdminServiceTest.java` | Modified | `+4` tests for `changeBaseUnit`; `FakeProductRepositoryPort.setBaseUnit` now functional; `withStockPresencePort` helper; `setUp` passes `Optional.empty()` |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 7 tasks 7.1–7.8 marked `[x]` |

### Task 7.5 verification (run, not asserted from memory)

`rg 'base-unit' backend/src/main` — **not empty**, exit 0, 8 matches. Every match is explanatory prose in a comment or Javadoc:

```
catalog/application/service/ProductAdminService.java:105   // ... no base-unit homonym with factor != 1 ...
catalog/application/service/ProductUnitAdminService.java:74 // ... name, base-unit homonym ...
catalog/application/port/in/ManageProductUnitsUseCase.java:39  * ... a base-unit homonym with a factor other than 1 (R-13)
catalog/application/port/in/ManageProductUnitsUseCase.java:57  * ... a base-unit homonym with a factor other than 1 (R-13)
shared/stock/ProductStockPresencePort.java:19             * ... RN-13 exists to stop a base-unit change ...
catalog/domain/exception/BaseUnitChangeRejectedException.java:23  * Why the base-unit change was refused ...   (new in S7, prose)
catalog/domain/model/StockPresence.java:4                 * ... before a base-unit change (design §3.2 ...   (new in S7, prose)
catalog/domain/model/ProductUnit.java:18                  * <p>The base-unit homonym rule ...
```

Precise checks of the **substantive** constraint (no HTTP surface for the base-unit change) — all green:
- `rg -n '"/api/catalog[^"]*base' backend/src/main` → nothing. No base-unit route in `ProductController` or anywhere.
- `SecurityConfig.java` catalog matchers: still exactly `requestMatchers(GET, "/api/catalog/**").authenticated()` then `requestMatchers("/api/catalog/**").hasAuthority("ADMIN")` — unchanged since S3.
- `CatalogExceptionHandler` `@ExceptionHandler` list unchanged (12 handlers); its Javadoc note "`BaseUnitChangeRejectedException` deliberately gets no mapping" is intact. No new error code.

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `cd backend && ./mvnw test -Dtest=BaseUnitChangePolicyTest` → green (3/3); `-Dtest=ProductAdminServiceTest` → green (18/18). Run inside the full `verify`: surefire total `Tests run: 154, Failures: 0, Errors: 0, Skipped: 0` (was 147 at S6; +3 `BaseUnitChangePolicyTest` +4 `ProductAdminServiceTest` = +7). `ModuleBoundariesTest` 5/5, `SharedIsFrameworkFreeTest` 1/1 stay green with the new `StockPresence` / `BaseUnitChangeRejectedException` / `BaseUnitChangePolicy` in `catalog/domain`. |
| Runtime harness command/scenario and exact result | `N/A` for a new runtime path — S7 ships no `*IT` (tasks `Suggested Work Units`: S7 runtime harness = `none`; DT-07 defers the endpoint). The existing failsafe suite was still run as a regression guard: `Tests run: 79, Failures: 0, Errors: 0, Skipped: 0` — unchanged from S6, so wiring the 4-arg `ProductAdminService` constructor broke no `@SpringBootTest` context (Spring supplies `Optional.empty()`). |
| Rollback boundary | `git revert` of this commit deletes `StockPresence`, `BaseUnitChangeRejectedException`, `BaseUnitChangePolicy` and `BaseUnitChangePolicyTest`, restores `ProductAdminService`'s 3-arg constructor and the `changeBaseUnit` `UnsupportedOperationException` body, and reverts the `ProductAdminServiceTest` additions (incl. the `setBaseUnit` fake). R-08 goes back to "declared, not wired". Nothing else in `catalog` or `iam` is touched; no endpoint, matcher or error code existed to remove. |

### Final gate output (run, not asserted from memory)

**`cd backend && ./mvnw verify`** — `BUILD SUCCESS`, total time 01:17 min.

Surefire (`./mvnw test`, no Docker):

```
[INFO] Running com.optiplant.inventory.catalog.application.service.ProductAdminServiceTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 -- in ...ProductAdminServiceTest
[INFO] Running com.optiplant.inventory.catalog.domain.service.BaseUnitChangePolicyTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in ...BaseUnitChangePolicyTest
[INFO] Running com.optiplant.inventory.catalog.domain.service.ProductUnitPolicyTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0 -- in ...ProductUnitPolicyTest
[INFO] Running com.optiplant.inventory.ModuleBoundariesTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in ModuleBoundariesTest
[INFO] Running com.optiplant.inventory.SharedIsFrameworkFreeTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- in SharedIsFrameworkFreeTest
[INFO] Results:
[INFO] Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
```

Failsafe (`*IT`, Testcontainers / real PostgreSQL 17):

```
[INFO]   AuditAtomicityIT       Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
[INFO]   CategoryCatalogIT      Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ProductCatalogIT       Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ProductUnitCatalogIT   Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 79, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The single `ERROR`-level stack trace in the log is `AuditAtomicityIT`'s own deliberate `AtomicityFixtureFailure` (audit-write rollback fixture) — same benign line noted in every prior slice's progress; that suite reports `Tests run: 2, Failures: 0, Errors: 0`.

### Deviations from design

1. **`changeBaseUnit` writes an audit entry on the success path.** Task 7.4's one-line summary names only "port call, policy, `setBaseUnit` write" in one transaction, but design §8.1's `changeBaseUnit` row lists the atomic unit as "port call + `UPDATE products` + **audit**, in one transaction", design §5.2 says "Each mutation is: load → apply domain rule → persist → `auditWritePort.record(...)`", and R-15 requires every master-data mutation to leave a trail. So the success path records an `AuditAction.UPDATE` entry on `"products"` with `branchId = null` and before/after payloads, exactly like `edit`. A refusal throws before the write, so no audit row — consistent with how `create`/`edit` behave on rejection. Not a design departure; it follows §8.1/§5.2/R-15 where the task summary was terse.
2. **Task 7.5's literal `rg 'base-unit' backend/src/main` "returns nothing" cannot pass and could not since S1.** Six of the eight current hits pre-date S7 (S1–S6 explanatory comments about the "base-unit homonym" rule and the `ProductStockPresencePort` Javadoc); S7 adds two more, also prose. None is an HTTP route, route matcher, error code or handler mapping. The task's *intent* — PA-08 / contract §7 / design §3.4: no HTTP surface for the base-unit change — is unambiguous and fully met (verified precisely above). Treated as an imprecise verification command, not a design flaw, so no stop-and-report and no redesign.
3. **`changeBaseUnit` does not re-check the product's category active state** (unlike `enable`, R-11). R-08's scenarios and design §4.2 gate the operation solely on `StockPresence`; a base-unit change is orthogonal to category lifecycle and the contract names no category precondition for it. Left as the design specifies.

### Issues found

None that block S7. `design.md` §3.2, §3.4, §4.2, §5.2, §8.1 and contract §2.2, §7, §8, §11, §12.3 (PA-08) were complete and internally consistent for this slice. The only wrinkles are the terse task-7.4 audit phrasing (resolved by following §8.1/§5.2/R-15) and the imprecise task-7.5 `rg` wording (documented, intent met) — neither needed a redesign.

### Workload / PR boundary

- Mode: chained PR slice — PR7 of 8 (feature-branch-chain; PR7 targets PR6's branch `feat/ep-02-catalog-07-s6-unidades`).
- Current work unit: S7 — the base-unit rule, without its endpoint.
- Boundary: starts from the S6 branch; ends with `StockPresence`, `BaseUnitChangeRejectedException` (+ `Reason`), `BaseUnitChangePolicy`, the `ProductAdminService.changeBaseUnit` wiring against `Optional<ProductStockPresencePort>` (fail-closed), `BaseUnitChangePolicyTest` and the `ProductAdminServiceTest` extension. No endpoint, matcher, error code or handler mapping — `base_unit` stays de-facto immutable in this change (DT-07). Only S8 (cross-cutting verification) remains.
- Estimated review budget impact: ~110 lines production + ~110 lines tests (design forecast: ~170). Planned S7 slice of the approved 8-PR chain; well within the 400-line soft budget.

---

## Phase 8 — S8: Cross-cutting verification and documentation (PR8)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-09-s8-transversal` (chained on S7).
**Status**: 16/16 tasks complete. `./mvnw verify` + `validar_esquema.sh` + `validar_trazabilidad.py` all green. Ready for verify. **Last slice.**

### Completed tasks

- [x] 8.1 `CatalogRbacIT` (Testcontainers, real PostgreSQL 17). `operatorAndBranchManagerGet403OnEveryMutationAnd200OnEveryRead` — for `operador.bogota` **and** `gerente.bogota`: all **11** §6 mutation endpoints (4 category + 4 product + 3 unit) → `403`, all **5** read endpoints (2 category + 2 product + 1 unit) → `200`. The `403` targets seeded ids, so the filter-chain rejection is exercised without the request mutating anything (design §7, contract §5 note 3). `adminSeesExactlyWhatAnOperatorSeesOnEveryReadEndpoint` — raw response bodies from `admin.corp` and `operador.bogota` are byte-identical on every read endpoint (R-01, R-16).
- [x] 8.2 `CatalogRbacIT.noTokenAndExpiredTokenBothYield401OnEveryEndpoint` — all 16 endpoints, each with (a) no `Authorization` header and (b) an already-expired HS256 token minted with the app's own secret (mirrors `AuthenticationFlowIT.mintAccessToken`, no sleep) → `401` (R-01).
- [x] 8.3 `CatalogRbacIT.corporateAdminWithNullBranchCanPerformEveryMutation` — the seeded `admin.corp` (`users.branch_id = NULL`) runs a full cycle: create+edit category, create+edit product, add+replace+delete unit, disable+enable product, disable+enable category → every status `2xx`. No rule in the module inspects the principal's branch (contract §5 note 2).
- [x] 8.4 `CatalogRbacIT.twoUsersFromDifferentBranchesReadTheSameProductByteForByte` — `operador.bogota` (branch 1) and `operador.medellin` (branch 2) `GET` the seeded product detail and its units; both response bodies compare equal (R-16).
- [x] 8.5 `CatalogAuditIT` (modelled on `AuditAtomicityIT`). `everyMutationAcrossTheThreeResourcesWritesAnAuditRowWithNullBranch` — category create/edit/disable/enable, product create/edit/disable/enable, unit add/replace/delete; for each the `audit_logs` row is asserted: `action` matches (`CREATE`/`UPDATE`/`DISABLE`/`ENABLE`/`DELETE`), `entity_name` ∈ {`categories`,`products`,`product_units`}, `entity_id` = the affected `external_id`, `user_id` = `admin.corp`'s id, **`branch_id IS NULL`**, `payload_after` present on create (and `payload_before` null), both present on update/disable/enable, `payload_before` present + `payload_after` null on delete (R-15, R-16). `aFailingAuditWriteRollsTheCatalogMutationBack` — a valid unexpired `ADMIN` bearer whose `sub` is a random UUID no `users` row carries: authorization passes (the chain never hits the DB), the `categories` row is inserted, then `AuditWriteAdapter.requireUserId` cannot resolve the actor and throws **inside the same `@Transactional`** → response `5xx`, `SELECT count(*) FROM categories WHERE name = ?` → `0`, `audit_logs` count for `categories` unchanged. This is the atomic-effects invariant proven end-to-end for `catalog`.
- [x] 8.6 `CatalogApiContractIT.noNumericIdLeaksInAnyBodyOrLocationHeaderAcrossAllSixteenEndpoints` — every one of the 16 endpoints is exercised as `admin.corp`; each JSON body is walked recursively and the test **fails on any field literally named `id`** or any `*Id`/`*_id` field holding an integral value (external ids are UUID strings; `activeProductCount` is not `*Id`). Every `Location` header equals `/api/catalog/.../{externalId}` and, with UUIDs stripped, contains no digit (§7.1 point 1).
- [x] 8.7 `CatalogApiContractIT.openApiPublishesTheSixteenEndpointsTheErrorEnvelopeAndNoBaseUnitRoute` — `GET /v3/api-docs` (permitAll, no token): all **16** `METHOD path` pairs present in `paths` (with springdoc's `{externalId}` / `{productExternalId}` / `{unitExternalId}` templating); no catalog path contains `base-unit`/`baseunit` (PA-08); the `{ code, message }` error envelope is a documented schema. **See the minimal production change below.**
- [x] 8.8 **Verified, not re-created.** `docs/deuda_tecnica.md`: `DT-07` + `DT-08` registry rows (`:43-44`), `### DT-07` ficha (`:222`), `### DT-08` ficha (`:248`), version-history row `1.1` (`:7`). Re-read both fichas against the shipped code — `DT-07` still correctly states `base_unit` is de-facto immutable and `PUT /api/catalog/products/{externalId}` rejects the field with `400 invalid_request`; `DT-08` still correctly states the free-text search resolves by sequential scan and that `idx_products_sku` serves only the SKU-uniqueness equality check. No divergence, no ficha edit.
- [x] 8.9 Manual review checklist — all pass: direct subpackages of `com.optiplant.inventory` are only `catalog/`, `iam/`, `shared/` (no `config/`/`util/`); `catalog/infrastructure/config/` does not exist; `catalog/domain/**` framework-freedom and no cross-module import are enforced green by `ModuleBoundariesTest` (5/5) and re-checked by `rg`; `shared/stock/ProductStockPresencePort` declares exactly one `boolean isProductUntouched(UUID)` and no stock-shaped return type.
- [x] 8.10 `rg -i 'branch_inventories|kardex_movements' backend/src/main/java/com/optiplant/inventory/catalog` — **substantive constraint met**: `0` matches under `catalog/infrastructure/**`, and no `@Query`/native query/`jdbcTemplate` string anywhere in `catalog` names those tables. The literal command returns **2** lines, both Javadoc prose in `catalog/domain/model/StockPresence.java` naming the two tables to *explain* the R-08 precondition concept — not SQL. Same class of imprecise verification wording as S7's task 7.5 (`rg 'base-unit'`): recorded here, not treated as a design flaw and not redesigned (contract §2.2 rejected alternative 2 is honoured — `catalog` issues no SQL against `inventory`'s tables).
- [x] 8.11 `rg -n 'ROLE_|hasRole\(' backend/src/main/java/com/optiplant/inventory/catalog backend/src/main/java/com/optiplant/inventory/iam/infrastructure/config` — one hit, `SecurityConfig.java:29`, **inside the class Javadoc** ("nunca `hasRole()`, que antepone `ROLE_`"). Zero hits in `catalog`. Passes as specified ("no occurrence outside a doc comment").
- [x] 8.12 All three gates run for real, all green — literal output below.
- [x] 8.13 `git diff --stat $(git merge-base main HEAD) HEAD -- docs/` → exactly `docs/deuda_tecnica.md` (+72) and `docs/diagrama_er.md` (+3), both deliberate (design §10.4, D-12). `docs/casos_de_uso.md`, `docs/especificacion_requerimientos.md`, `docs/historias_de_usuario.md` untouched — no `RF`/`RNF`/`RN` identifier created, traceability matrix intact. `validar_trazabilidad.py` green.
- [x] 8.14 Contract §11 walked end to end — see the dedicated section below. Every item satisfied; one item ("error envelope documented in `/v3/api-docs`") required a minimal doc-only production change.
- [x] 8.15 `ProductSearchPerformanceIT` (`@TestInstance(PER_CLASS)`, real PostgreSQL 17). `@BeforeAll` bulk-inserts **10 000** products in one statement — `INSERT INTO products (category_id, sku, name, base_unit) SELECT ((g-1)%4)+1, 'PERF-'||LPAD(g::text,6,'0'), 'Perf product '||g, 'KG' FROM generate_series(1, 10000) g` — asserts `count(*) FROM products >= 10000`, and `@AfterAll` deletes the `PERF-%` rows so the shared container is left clean. **(a)** `aContainsSearchStaysUnderTheLatencyCeilingAtTenThousandProducts` — 5 warmup + 25 measured `GET /api/catalog/products?q=npk&size=20` calls, timed with `System.nanoTime()`; **median = 41 ms, p95 = 57 ms** (a fresh re-run: median 41 ms, p95 57 ms, max 61 ms), ceiling **200 ms** → PASS with wide margin; each run also asserts `q=npk` still finds the seeded `FERT-NPK-151515` (≥ 1 hit). **(b)** `explainReportsASequentialScanOnProductsForTheContainsPredicate` — `EXPLAIN` of the contains predicate (no `ORDER BY`/`LIMIT`, so nothing tempts the planner toward `idx_products_sku`) → `Seq Scan on products p` (full plan below). Confirms design §8.4 / corrected contract §9.
- [x] 8.16 **Not triggered.** 8.15 (a) passed with median 41 ms « 200 ms, so `DT-08`'s "latency test fails its threshold" trigger did not fire. No `CREATE EXTENSION pg_trgm`, no `gin_trgm_ops` index, no schema change; `DT-08` stays **Aceptada** and its ficha is unchanged (a debt is only marked **Resuelta** when actually paid).

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/src/main/java/com/optiplant/inventory/catalog/infrastructure/adapter/in/web/CatalogExceptionHandler.java` | Modified | **Doc-only.** Added `io.swagger.v3.oas.annotations` imports and three `@ApiResponse` annotations (400 on `onIllegalArgument`, 404 on `onCategoryNotFound`, 409 on `onCategoryInUse`) each referencing `@Schema(implementation = ErrorResponse.class)` via a `private static final String ERROR_ENVELOPE_MEDIA_TYPE` constant. Zero behaviour change — status codes, codes, messages and the `ErrorResponse` record are untouched; springdoc now emits the `{ code, message }` envelope into `/v3/api-docs`, satisfying contract §11's DoD item. |
| `backend/src/test/java/com/optiplant/inventory/CatalogRbacIT.java` | Created | 5 tests — 8.1 (×2), 8.2, 8.3, 8.4. Endpoint catalogue as a `List<Endpoint>`; expired-token mint via `NimbusJwtEncoder`. |
| `backend/src/test/java/com/optiplant/inventory/CatalogAuditIT.java` | Created | 2 tests — 8.5 positive (every mutation → audit row, `branch_id NULL`) + negative (orphan-actor bearer → audit write throws in-txn → full rollback). |
| `backend/src/test/java/com/optiplant/inventory/CatalogApiContractIT.java` | Created | 2 tests — 8.6 (no numeric id across all 16 endpoints, recursive JSON walk + `Location` check) and 8.7 (`/v3/api-docs`: 16 ops published, error envelope documented, no base-unit route). |
| `backend/src/test/java/com/optiplant/inventory/ProductSearchPerformanceIT.java` | Created | 2 tests — 8.15 (a) warmed-up median/p95 latency over 25 runs at 10 000 products, and (b) `EXPLAIN` → sequential scan. `@BeforeAll` bulk seed, `@AfterAll` cleanup. |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 8 tasks 8.1–8.16 marked `[x]` with per-task evidence notes. |

### Work Unit Evidence

| Evidence | Value |
|---|---|
| Focused test command and exact result | `cd backend && ./mvnw verify -Dit.test=CatalogApiContractIT` → `BUILD SUCCESS`, `Tests run: 2, Failures: 0`. Full failsafe suite: `Tests run: 90, Failures: 0, Errors: 0, Skipped: 0` (was 79 at S7 → +11 = `CatalogRbacIT` 5 + `CatalogAuditIT` 2 + `CatalogApiContractIT` 2 + `ProductSearchPerformanceIT` 2). |
| Runtime harness command/scenario and exact result | `cd backend && ./mvnw verify` — real PostgreSQL 17 via Testcontainers — `BUILD SUCCESS`. surefire `Tests run: 154, Failures: 0, Errors: 0, Skipped: 0`; `ModuleBoundariesTest` 5/5; `SharedIsFrameworkFreeTest` 1/1. Plus `./scripts/validar_esquema.sh` (30 checks) and `python3 scripts/validar_trazabilidad.py` — both exit 0. |
| Rollback boundary | `git revert` of this commit deletes the four new `*IT` classes and reverts the three `@ApiResponse` annotations + two imports on `CatalogExceptionHandler` (behaviour identical either way). No schema, seed, doc, or other production file is touched; the `PERF-%` seed rows are self-cleaning (`@AfterAll`). |

### Final gate output (run, not asserted from memory)

**`cd backend && ./mvnw verify`** — `BUILD SUCCESS`, total time 01:25 min:

```
[INFO] --- surefire ---
[INFO] Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ModuleBoundariesTest        Tests run: 5, Failures: 0, Errors: 0
[INFO]   SharedIsFrameworkFreeTest   Tests run: 1, Failures: 0, Errors: 0

[INFO] --- failsafe (*IT, Testcontainers / real PostgreSQL 17) ---
[INFO]   AuditAtomicityIT       Tests run: 2, Failures: 0, Errors: 0
[INFO]   AuditLogQueryIT        Tests run: 8, Failures: 0, Errors: 0
[INFO]   AuthenticationFlowIT   Tests run: 12, Failures: 0, Errors: 0
[INFO]   BranchAdminIT          Tests run: 9, Failures: 0, Errors: 0
[INFO]   BranchIsolationIT      Tests run: 6, Failures: 0, Errors: 0
[INFO]   CatalogApiContractIT   Tests run: 2, Failures: 0, Errors: 0
[INFO]   CatalogAuditIT         Tests run: 2, Failures: 0, Errors: 0
[INFO]   CatalogRbacIT          Tests run: 5, Failures: 0, Errors: 0
[INFO]   CategoryCatalogIT      Tests run: 8, Failures: 0, Errors: 0
[INFO]   ProductCatalogIT       Tests run: 9, Failures: 0, Errors: 0
[INFO]   ProductSearchPerformanceIT  Tests run: 2, Failures: 0, Errors: 0
[INFO]   ProductUnitCatalogIT   Tests run: 9, Failures: 0, Errors: 0
[INFO]   UserAdminIT            Tests run: 15, Failures: 0, Errors: 0
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Two `ERROR`-level stack traces in the log are the deliberate in-test failures: `AuditAtomicityIT`'s `AtomicityFixtureFailure`, and `CatalogAuditIT`'s `IllegalStateException: No user found for external id <uuid>` (the orphan-actor rollback probe). Both suites report `Failures: 0, Errors: 0`.

**8.15 latency + EXPLAIN (run, not asserted from memory):**

```
[8.15] contains-search q=npk size=20 over 10000 products, 25 measured runs: median=41ms p95=57ms max=61ms
[8.15] EXPLAIN for the contains predicate:
Aggregate  (cost=211.33..211.34 rows=1 width=8)
  ->  Hash Join  (cost=15.62..211.13 rows=79 width=0)
        Hash Cond: (p.category_id = c.id)
        ->  Seq Scan on products p  (cost=0.00..195.30 rows=79 width=8)
              Filter: (is_active AND ((lower((sku)::text) ~~ '%npk%'::text) OR (lower((name)::text) ~~ '%npk%'::text)))
        ->  Hash  (cost=12.50..12.50 rows=250 width=8)
              ->  Seq Scan on categories c  (cost=0.00..12.50 rows=250 width=8)
```

Median 41 ms « 200 ms ceiling → 8.16 (DT-08 trigger) did **not** fire. `Seq Scan on products` confirms design §8.4 / contract §9.

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

`A. ... ok 20 tablas creadas (20)` still passes.

**`python3 scripts/validar_trazabilidad.py`** — exit 0:

```
4. Ítems de deuda técnica con ficha detallada
   ok    8 declarados, 8 con ficha
5. Enlaces relativos entre documentos
   ok    38 enlaces revisados, 0 rotos
--------------------------------------------------------------
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 8 DT
```

### Contract §11 walk (task 8.14) — every item, with where it is proven

**Automated verification**

| Item | Status |
|---|---|
| `./mvnw verify` green (ArchUnit + Testcontainers) | ✅ BUILD SUCCESS, surefire 154 / failsafe 90 |
| `./scripts/validar_esquema.sh` green (mandatory — `init-db/` changed) | ✅ 30 comprobaciones correctas |
| `python3 scripts/validar_trazabilidad.py` green | ✅ trazabilidad íntegra |
| No `RF`/`RNF`/`RN` identifier created, `casos_de_uso.md` matrix untouched | ✅ 8.13 — docs diff is only `diagrama_er.md` + `deuda_tecnica.md` |

**New invariants in `validar_esquema.sh`** (added in S1, re-verified green here)

| Item | Status |
|---|---|
| `rechaza` two default sale units for one product | ✅ section G |
| `acepta` two different products each with their own default | ✅ section G |
| `igual` `count(*) categories WHERE is_active IS NULL` = 0 | ✅ section G |
| `igual` seeded categories active = 4 | ✅ section G |
| `igual "20 tablas creadas"` still passes | ✅ validator green |

**Domain unit tests** (`*Test`, no Docker) — all present and green (S2/S4/S6/S7)

| Item | Where |
|---|---|
| SKU normalization `abc-1` ≡ `ABC-1` | `SkuTest` |
| Base-unit / `unit_name` format + whitespace rejection | `UnitCodeTest` |
| Base-unit rule: applied / balances / kardex-only / port-unavailable | `BaseUnitChangePolicyTest` + `ProductAdminServiceTest` |
| Conversion factor ≤ 0 rejected | `ProductUnitPolicyTest` / `ProductInvariantsTest` |
| Base-unit homonym factor ≠ 1 rejected | `ProductInvariantsTest` / `ProductUnitPolicyTest` |
| Marking a new default leaves exactly one | `ProductUnitPolicyTest` |
| Category name normalization + case-insensitive compare | `CategoryNameTest` |

**Integration tests** (`*IT`, Testcontainers)

| Item | Where |
|---|---|
| Full category cycle | `CategoryCatalogIT` |
| `409 category_in_use` + success with only inactive products | `CategoryCatalogIT` |
| `409 category_inactive` on create + on re-enable | `ProductCatalogIT` |
| Full product cycle incl. inline units, read by `external_id` | `ProductCatalogIT` |
| `409 duplicate_sku` on create **and** edit | `ProductCatalogIT` |
| `400` when `PUT /products/{id}` carries `baseUnit`; no OpenAPI route mutates a base unit | `ProductCatalogIT` (5.10) + `CatalogApiContractIT` (8.7) |
| Replacing the default sale unit **commits** (one `TRUE` row) | `ProductUnitCatalogIT` (6.9/6.10) |
| Schema-level rejection of a second default → conflict not 500 | `ProductUnitCatalogIT` (6.12) |
| Free-text search latency at 10 000 products + `EXPLAIN` sequential scan | `ProductSearchPerformanceIT` (8.15) |
| RBAC: `403` on every mutation, `200` on every read | `CatalogRbacIT` (8.1) |
| `401` with no token on any endpoint | `CatalogRbacIT` (8.2) |
| Listing active-only default; `active=false`/`all`; `active=maybe` → `400` | `CategoryCatalogIT` + `ProductCatalogIT` |
| Page cap with `size=5000`; `sort` outside allow-list → `400` | `ProductCatalogIT` (5.11) |
| Every response contains no numeric `id` (explicit JSON assertion) | `CategoryCatalogIT` (3.10) + `CatalogApiContractIT` (8.6 — all 16) |
| Disabling a product with stock leaves balances intact | `ProductCatalogIT` (5.12) |
| Every mutation writes its `audit_logs` entry with null `branch_id`; audit failure rolls back | `CatalogAuditIT` (8.5) |

**Manual review**

| Item | Status |
|---|---|
| No new class in a direct subpackage of `com.optiplant.inventory` other than `catalog/` | ✅ 8.9 |
| `catalog/domain/**` imports neither `org.springframework..` nor `jakarta.persistence..` | ✅ `ModuleBoundariesTest` + `rg` |
| `catalog` imports no class from another business module; `shared` still imports no module | ✅ `ModuleBoundariesTest` 5/5 |
| Stock-presence port: one boolean method, no stock-shaped return type | ✅ `ProductStockPresencePort` |
| No `catalog` adapter issues SQL against `branch_inventories` / `kardex_movements` | ✅ 8.10 (0 hits in `infrastructure`; 2 Javadoc-prose hits in `domain/model` only) |
| The §6 endpoints appear in `/v3/api-docs` with the error envelope documented | ✅ 8.7 — **required action**: springdoc did not synthesise the envelope from the runtime-computed `ResponseEntity.status(...)` handlers, so three doc-only `@ApiResponse` annotations were added to `CatalogExceptionHandler` (400/404/409 → `ErrorResponse` schema). No behaviour change. |

**Result of the walk: no §11 item left unsatisfied.** The only item that was not already met by S1–S7 was the OpenAPI error-envelope documentation, now fixed with the minimal doc-only change above.

### Deviations from design / tasks

1. **Task 8.7 required a minimal production change to `CatalogExceptionHandler` (doc-only).** Contract §11 and RNF-API-01 require the `{ code, message }` envelope to appear in `/v3/api-docs`. springdoc-openapi does not auto-document responses for `@ExceptionHandler` methods that compute their status at runtime via `ResponseEntity.status(...)` (it has no static status to attach) — `iam`'s `IamExceptionHandler` has the identical gap. Three `@ApiResponse` (`io.swagger.v3.oas.annotations`) annotations were added to the catalog handler's 400/404/409 methods, each pointing at `@Schema(implementation = ErrorResponse.class)`. This changes only the generated OpenAPI document; every status code, error `code` string, message and the `ErrorResponse` record itself are untouched, and no test other than 8.7 changed behaviour. Treated as the "a transversal test revealed a real gap → minimal fix + document" path the S8 brief allows, not a redesign.
2. **Task 8.10's literal `rg` command cannot return empty and never could.** `rg -i 'branch_inventories|kardex_movements' .../catalog` matches 2 Javadoc lines in `catalog/domain/model/StockPresence.java` that name the two tables to explain the R-08 concept. The *substantive* rule — no `catalog` adapter issues SQL against those tables — holds precisely (0 hits in `catalog/infrastructure`, no `@Query`/native/`jdbcTemplate` referencing them). Same imprecise-wording pattern as S7's task 7.5; documented, not escalated.
3. **Task 8.11's `rg` matches one line — inside a doc comment.** `SecurityConfig.java:29` Javadoc says "nunca `hasRole()`". The task's own wording ("no occurrence outside a doc comment") accepts this; recorded for completeness.

### Issues found

None that block S8. `design.md` §7, §8.1, §8.4 and `contract.md` §5, §7, §9, §11 were complete and internally consistent for this slice. The one real gap uncovered (OpenAPI error envelope not documented) is a contract-DoD item that S1–S7 never addressed; it is fixed here with the minimal doc-only annotation change described above rather than deferred or dropped.

### Workload / PR boundary

- Mode: chained PR slice — PR8 of 8 (feature-branch-chain; PR8 targets PR7's branch `feat/ep-02-catalog-08-s7-regla-unidad-base`). **Final slice — this PR completes `add-catalog-module`.**
- Current work unit: S8 — cross-cutting verification and documentation.
- Boundary: starts from the S7 branch; ends with `CatalogRbacIT`, `CatalogAuditIT`, `CatalogApiContractIT`, `ProductSearchPerformanceIT`, and the three doc-only `@ApiResponse` annotations on `CatalogExceptionHandler`. No new module code, no schema change, no seed change, no `docs/` change (S1's `diagrama_er.md` / `deuda_tecnica.md` edits already carried the deliberate doc deltas).
- Estimated review budget impact: ~660 lines of new `*IT` test code + ~8 lines of production annotation (design forecast: ~320, all tests). Above the 400-line soft budget but this is the planned S8 slice of the approved 8-PR feature-branch chain, and it is entirely additive test code plus a doc-only annotation — it proceeds as a bounded slice per the tasks `Review Workload Forecast`.
