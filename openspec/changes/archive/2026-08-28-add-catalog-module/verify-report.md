```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:f745d142411bc8dd9791ce803b4f279fef7f5c2df90b549af7fe38507cd8584e
verdict: pass
blockers: 0
critical_findings: 0
requirements: 16/16
scenarios: 47/47
test_command: cd backend && ./mvnw verify
test_exit_code: 0
test_output_hash: sha256:f745d142411bc8dd9791ce803b4f279fef7f5c2df90b549af7fe38507cd8584e
build_command: cd backend && ./mvnw verify
build_exit_code: 0
build_output_hash: sha256:f745d142411bc8dd9791ce803b4f279fef7f5c2df90b549af7fe38507cd8584e
```

# Verification Report — `add-catalog-module`

- **Change**: `add-catalog-module` (EP-02 Catalog, CU-INV-01 / CU-INV-02)
- **Mode**: Standard — OpenSpec artifact store, `strict_tdd: false`
- **Branch verified**: `feat/ep-02-catalog-09-s8-transversal` @ `f9315f1` — the full S1→S8 chain, final state
- **Artifacts read**: `contract.md` (867 lines), `design.md` (1257 lines), `tasks.md` (162 lines), `apply-progress.md` (894 lines)
- **Verdict**: **PASS** — verified. 0 CRITICAL, 0 WARNING, 2 SUGGESTION.

## Completeness

| Dimension | Result |
|---|---|
| Tasks complete | 102 / 102 `[x]` (S1 13, S2 12, S3 11, S4 15, S5 13, S6 14, S7 8, S8 16) |
| Contract §11 Definition of Done | every item satisfied (walk below) |
| Design decisions D-1…D-16 | all reflected in final code |
| CLAUDE.md invariants | all upheld |
| Behavioural requirements R-01…R-16 | 16 / 16 covered by passing tests |
| Scenarios (Given/When/Then in contract §4) | 47 / 47 covered |

## Gate output (run by verify, not copied from apply-progress)

### 1. `cd backend && ./mvnw verify` — exit 0, BUILD SUCCESS

```
[INFO] --- surefire ---
[INFO] Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ModuleBoundariesTest        Tests run: 5, Failures: 0, Errors: 0
[INFO]   SharedIsFrameworkFreeTest   Tests run: 1, Failures: 0, Errors: 0
[INFO]   catalog...CategoryAdminServiceTest   Tests run: 13
[INFO]   catalog...ProductAdminServiceTest    Tests run: 18
[INFO]   catalog...ProductInvariantsTest      Tests run: 8
[INFO]   catalog...SkuTest / UnitCodeTest / ActiveFilterTest / CategoryNameTest   9 / 11 / 7 / 10
[INFO]   catalog...BaseUnitChangePolicyTest   Tests run: 3
[INFO]   catalog...ProductUnitPolicyTest      Tests run: 8

[INFO] --- failsafe (*IT, Testcontainers / real PostgreSQL 17) ---
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO]   ApplicationContextIT        Tests run: 1
[INFO]   AuditAtomicityIT            Tests run: 2
[INFO]   AuditLogQueryIT             Tests run: 8
[INFO]   AuthenticationFlowIT        Tests run: 12
[INFO]   BranchAdminIT               Tests run: 9
[INFO]   BranchIsolationIT           Tests run: 6
[INFO]   CatalogApiContractIT        Tests run: 2
[INFO]   CatalogAuditIT             Tests run: 2
[INFO]   CatalogRbacIT              Tests run: 5
[INFO]   CategoryCatalogIT          Tests run: 8
[INFO]   ProductCatalogIT          Tests run: 9
[INFO]   ProductSearchPerformanceIT Tests run: 2
[INFO]   ProductUnitCatalogIT      Tests run: 9
[INFO]   UserAdminIT               Tests run: 15
[INFO] BUILD SUCCESS
```

Two `ERROR`-level stack traces in the log are the deliberate in-test failures
(`AuditAtomicityIT.AtomicityFixtureFailure` and `CatalogAuditIT`'s orphan-actor
rollback probe `IllegalStateException: No user found for external id <uuid>`);
both suites report `Failures: 0, Errors: 0`.

Search-latency probe (`ProductSearchPerformanceIT`, 10 000 products, 25 measured runs):
`median = 43 ms, p95 = 48 ms, max = 50 ms` against a 200 ms ceiling. `EXPLAIN` of the
contains predicate → `Seq Scan on products p` (matches design §8.4 / corrected contract §9).
`DT-08` trigger did not fire.

### 2. `./scripts/validar_esquema.sh` — exit 0

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

`A. ... ok 20 tablas creadas (20)` and `ok 02-seed-data.sql` still pass — no table added.

### 3. `python3 scripts/validar_trazabilidad.py` — exit 0

```
4. Ítems de deuda técnica con ficha detallada
   ok    8 declarados, 8 con ficha
5. Enlaces relativos entre documentos
   ok    38 enlaces revisados, 0 rotos
--------------------------------------------------------------
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 8 DT
```

## Contract §11 — Definition of Done, walked item by item

### Automated verification
| Item | Result |
|---|---|
| `./mvnw verify` green (ArchUnit + Testcontainers) | PASS — BUILD SUCCESS, surefire 154 / failsafe 90, 0F/0E |
| `./scripts/validar_esquema.sh` green (mandatory — `init-db/` changed) | PASS — 30 comprobaciones correctas, exit 0 |
| `python3 scripts/validar_trazabilidad.py` green | PASS — trazabilidad íntegra, exit 0 |
| No `RF`/`RNF`/`RN` identifier created; `casos_de_uso.md` matrix untouched | PASS — `git diff <merge-base>..HEAD -- docs/` = only `deuda_tecnica.md` (+72) and `diagrama_er.md` (+3). `casos_de_uso.md`, `especificacion_requerimientos.md`, `historias_de_usuario.md` untouched (corrected DoD item, design §10.4 / D-12) |

### New invariants in `validar_esquema.sh` (section G, 25 → 30)
| Item | Result |
|---|---|
| `rechaza` two default sale units for one product | PASS — section G, run green |
| `acepta` two different products each with their own default | PASS — section G |
| `igual count(*) categories WHERE is_active IS NULL` = 0 | PASS |
| `igual` seeded categories active = 4 | PASS |
| `igual "20 tablas creadas"` still passes | PASS |

### Domain unit tests (no Docker)
| Item | Covering test (passed at runtime) |
|---|---|
| SKU normalization `abc-1` ≡ `ABC-1` (R-06) | `SkuTest` 9/9 |
| Base-unit / `unit_name` format + whitespace rejection (R-07) | `UnitCodeTest` 11/11 |
| Base-unit rule: applied / balances / kardex-only / port-unavailable (R-08) | `BaseUnitChangePolicyTest` 3/3 + `ProductAdminServiceTest` (4 `changeBaseUnit` cases) |
| Conversion factor ≤ 0 rejected (R-13) | `ProductUnitPolicyTest`, `ProductInvariantsTest` |
| Base-unit homonym factor ≠ 1 rejected (R-13) | `ProductInvariantsTest`, `ProductUnitPolicyTest` |
| Marking a new default leaves exactly one (R-14) | `ProductUnitPolicyTest` |
| Category name normalization + case-insensitive compare (R-02) | `CategoryNameTest` 10/10 |

### Integration tests (Testcontainers, real PostgreSQL 17)
| Item | Covering test (passed at runtime) |
|---|---|
| Full category cycle (create/edit/list/disable/enable) | `CategoryCatalogIT` 8/8 |
| `409 category_in_use` + success when only inactive products (R-04) | `CategoryCatalogIT` |
| `409 category_inactive` on create + on re-enable (R-05, R-11) | `ProductCatalogIT` |
| Full product cycle incl. inline units, read by `external_id` (R-10) | `ProductCatalogIT` 9/9 |
| `409 duplicate_sku` on create AND edit (R-06, R-09) | `ProductCatalogIT` |
| `400` when `PUT /products/{id}` carries `baseUnit`; no OpenAPI route mutates a base unit (§6.2, PA-08) | `ProductCatalogIT` (5.10) + `CatalogApiContractIT` (8.7) |
| Replacing the default sale unit COMMITS, exactly one `TRUE` row (R-14, S-3, design §8.2) | `ProductUnitCatalogIT` (6.9 / 6.10) |
| Schema-level rejection of a second default → conflict not 500 (R-14, S-3) | `ProductUnitCatalogIT` (6.12) |
| Free-text search latency at 10 000 products + `EXPLAIN` sequential scan (RNF-PER-01, design §8.4) | `ProductSearchPerformanceIT` (8.15) — median 43 ms, Seq Scan |
| RBAC: `403` on every §6 mutation, `200` on every read (R-01) | `CatalogRbacIT` (8.1) — 11 mutation × 2 roles → 403; 5 read × 2 roles → 200 |
| `401` with no/expired token on every endpoint (R-01) | `CatalogRbacIT` (8.2) — 16 endpoints |
| Corporate `ADMIN` with `branch_id = NULL` can perform every mutation (§5 note 2) | `CatalogRbacIT` (8.3) |
| Listing active-only default; `active=false`/`all`; `active=maybe` → `400` (R-12) | `CategoryCatalogIT` + `ProductCatalogIT` (5.11) |
| Page cap with `size=5000`; `sort` outside allow-list → `400`, never interpolated (R-12) | `ProductCatalogIT` (5.11) |
| Every response contains no numeric `id` — explicit JSON assertion, all 16 endpoints (§7.1) | `CatalogApiContractIT` (8.6) + `CategoryCatalogIT` (3.10) |
| Disabling a product with stock leaves `branch_inventories` intact (R-10) | `ProductCatalogIT` (5.12) |
| Every mutation writes `audit_logs` with `branch_id = NULL`; audit failure rolls the mutation back (R-15) | `CatalogAuditIT` (8.5) |

### Manual review
| Item | Result |
|---|---|
| No new class in a direct subpackage of `com.optiplant.inventory` other than `catalog/` | PASS — direct children: `catalog/`, `iam/`, `shared/`, `InventoryApplication.java` only. `catalog/infrastructure/config/` absent |
| `catalog/domain/**` imports neither `org.springframework..` nor `jakarta.persistence..` | PASS — `ModuleBoundariesTest` 5/5; `rg` finds only 2 Javadoc-prose mentions in `BaseUnitChangePolicy` / `ProductUnitPolicy` |
| `catalog` imports no class of another business module; `shared` still imports no module | PASS — `ModuleBoundariesTest` (`ningunModuloEntraAlInteriorDeOtro`, `sharedEsUnaHoja`, `noHayCiclosEntreModulos`) 5/5. Module graph `catalog → shared ← inventory`, acyclic |
| Stock-presence port: one `boolean` method, no stock-shaped return type (§2.2, PA-09) | PASS — `shared/stock/ProductStockPresencePort` = `boolean isProductUntouched(UUID)`, sole import `java.util.UUID` |
| No `catalog` adapter issues SQL against `branch_inventories` / `kardex_movements` (§2.2 rejected alt. 2) | PASS — 0 hits under `catalog/infrastructure/**`; no `@Query`/native/`jdbcTemplate` naming those tables. The 2 literal `rg` hits are Javadoc prose in `catalog/domain/model/StockPresence.java` |
| §6 endpoints in `/v3/api-docs` with the error envelope documented (RNF-API-01) | PASS — `CatalogApiContractIT` (8.7). Required a doc-only fix: 3 `@ApiResponse` annotations on `CatalogExceptionHandler` (400/404/409 → `ErrorResponse` schema) because springdoc does not synthesise responses from runtime-computed `ResponseEntity.status(...)` handlers — zero behaviour change |

## Design coherence

| design.md element | Final code | Status |
|---|---|---|
| Domain model records, VOs, enums (§3) | `catalog/domain/model/**` — `Product` compact ctor asserts R-13/R-14 (dup name → `DuplicateProductUnitException`, homonym factor ≠ 1 → `InvalidConversionFactorException`, > 1 default → `IllegalStateException`); `Sku`/`UnitCode`/`CategoryName` normalize once | Matches |
| Primary/secondary ports (§5.1, §5.3) | `application/port/in/*UseCase`, `application/port/out/*RepositoryPort` — mutations take `AuthenticatedPrincipal actor`, reads do not (D-7, R-16). `EditProductCommand` has no `baseUnit` | Matches |
| `BaseUnitChangePolicy` takes `StockPresence` enum, not the port / `Optional` (D-3, §4.2) | `catalog/domain/service/BaseUnitChangePolicy` — `switch` over 3 values, `UNKNOWN` throws `PRECONDITION_UNVERIFIABLE`; `ProductAdminService.presenceOf` maps the `Optional<ProductStockPresencePort>` with `.orElse(StockPresence.UNKNOWN)` — fail-closed | Matches |
| `ProductStockPresencePort` in `shared/stock`, one question (D-4, §2.2) | present, JDK-only, leaf | Matches |
| S-3 default-sale-unit write sequence (§8.2, trap resolved) | `ProductUnitPersistenceAdapter.add`/`replace`: `clearDefaultSaleUnit(...)` (`@Modifying(flushAutomatically=true, clearAutomatically=true)` bulk JPQL) **before** the row that ends `TRUE`; skipped for `defaultSaleUnit=false`; inline-`create` path documented as needing no clear. Proven by `ProductUnitCatalogIT` 6.9/6.10 (transaction commits, exactly one `TRUE`) | Matches |
| Authorization: two method-scoped matchers in `iam`'s `SecurityConfig` (D-1, §7) | `SecurityConfig`: `.requestMatchers(HttpMethod.GET, "/api/catalog/**").authenticated()` then `.requestMatchers("/api/catalog/**").hasAuthority("ADMIN")` before `.anyRequest()`. GET first, `hasAuthority` not `hasRole`. Only `iam` file touched (`git diff --stat` = `SecurityConfig.java` only) | Matches |
| Product search JPQL, never native (D-10) | `ProductSpringDataRepository.search` + `CategorySpringDataRepository` — no `nativeQuery` anywhere in `catalog`; S3's temporary native reads migrated to JPQL in S5 | Matches |
| Schema edits S-1/S-2/S-4/S-3 (§10.1) + ER diagram sync (D-12) + `DT-07`/`DT-08` (D-13) | `01-init-schema.sql` diff matches §10.1 verbatim; `diagrama_er.md` gains `is_active`/`updated_at`; `deuda_tecnica.md` has both registry rows + fichas; `CLAUDE.md` + `openspec/config.yaml` counters 25 → 30 | Matches |
| 10 error codes, `BaseUnitChangeRejectedException` unmapped (§6.3, §3.4) | `CatalogExceptionHandler` maps exactly `invalid_request`, `invalid_conversion_factor`, `category_not_found`, `product_not_found`, `product_unit_not_found`, `duplicate_category_name`, `duplicate_sku`, `duplicate_product_unit`, `category_in_use`, `category_inactive`. No `BaseUnitChangeRejectedException` handler; unidentifiable `DataIntegrityViolationException` rethrows → 500 (D-14) | Matches |
| Audit inside the transaction, `branchId = null` (R-15, §8.1) | all three services: `@Transactional` per mutation, `new AuditEntryCommand(actor.userId(), null, ...)`, `entityName` ∈ {categories, products, product_units}; `changeBaseUnit` audits on success only, refusal throws before any write | Matches |

## Deviations from `apply-progress.md` — re-checked against final code

| Deviation | Local? | Contract / port / API impact | Still true in final code? |
|---|---|---|---|
| S3 native product-side reads → JPQL in S5 | Yes | None — `hasActiveProducts(UUID)` / `CategorySummary.activeProductCount` unchanged | Yes — `CategorySpringDataRepository` is now JPQL over `ProductJpaEntity`; `rg 'nativeQuery' catalog` = 0 |
| S6 bulk `@Modifying` `deleteScoped` JPQL instead of `em.remove` (managed-collection reconcile gotcha) | Yes | None — port `delete(UUID, UUID)` signature unchanged; 404 validated by the service before the call; `ProductUnitCatalogIT` 6.13 green | Yes |
| S6 `IllegalStateException` → `400 invalid_request` scoped by `message.contains("default sale unit")` | Yes | None — reuses an existing §7 code, no taxonomy change; closes an S5 `500`-on-malformed-payload hole. `Product` R-14 message contains the matched phrase; `ProductUnitCatalogIT` asserts 400 + nothing persisted | Yes |
| S8 three doc-only `@ApiResponse` annotations on `CatalogExceptionHandler` | Yes | None — status codes, `code` strings, messages, `ErrorResponse` record all untouched; only `/v3/api-docs` output changes. Satisfies §11 "error envelope documented" | Yes |
| S7 `changeBaseUnit` fail-closed via `.orElse(StockPresence.UNKNOWN)` + policy throws on `UNKNOWN` | n/a — this is the contract (§2.2 "MUST NOT fail open", R-08 scenario 4), not a deviation | None | Yes — `ProductAdminService.presenceOf` + `BaseUnitChangePolicy`; `ProductAdminServiceTest.changeBaseUnitFailsClosedWhenNoStockPresencePortImplementationIsAvailable` green |
| S7 `changeBaseUnit` writes an audit row on the success path (task 7.4 summary was terse) | Yes | None — follows design §8.1 / §5.2 / R-15; refusal throws before any write so no audit row | Yes |
| S7 / S8 imprecise `rg 'base-unit'` / `rg 'branch_inventories|kardex_movements'` verification wording | Yes | None — every literal hit is Javadoc prose; the substantive constraints (no base-unit HTTP surface; no catalog SQL against inventory tables) hold precisely | Yes |

No deviation carries contract, port, or API-surface impact.

## Issues

### CRITICAL
None.

### WARNING
None.

### SUGGESTION
1. `CatalogExceptionHandler.onIllegalState` discriminates a client 400 from a server 500 by
   `message.contains("default sale unit")`. It is covered by `ProductUnitCatalogIT`, but the
   coupling to `Product`'s R-14 exception wording is implicit; a future reword of that message
   would silently regress the two-inline-default payload to `500`. Consider a dedicated
   domain exception for the R-14 aggregate violation (as R-13 already has
   `DuplicateProductUnitException` / `InvalidConversionFactorException`) so the mapping keys
   on a type, not a substring. Same reasoning applies to the constraint-name substring matching
   in `onDataIntegrityViolation`, though that one is explicitly sanctioned by design D-14.
2. `iam`'s `IamExceptionHandler` has the identical OpenAPI error-envelope gap that S8 fixed
   for `catalog` (noted in apply-progress S8). Out of scope here, but worth a follow-up so the
   two modules' published contracts stay symmetric.

## Verdict

**PASS — verified.** All 102 tasks are `[x]` and every claim they make holds against the final
code. Contract §11's Definition of Done is fully satisfied, with the one item that needed action
(OpenAPI error-envelope documentation) resolved by a zero-behaviour doc-only change. The design
model, ports, adapters, the S-3 default-sale-unit write sequence, the `shared/stock` port and the
acyclic `catalog → shared ← inventory` module graph all match `design.md`. Every CLAUDE.md
invariant holds. All three gates were re-run by this phase and are green:
`./mvnw verify` BUILD SUCCESS (154 surefire / 90 failsafe, 0F/0E), `./scripts/validar_esquema.sh`
30/30, `python3 scripts/validar_trazabilidad.py` intact with 8 DT fichas. No CRITICAL or WARNING
issue; two SUGGESTIONs recorded for future hardening. Ready for `sdd-archive`.
