```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:40d0959d419e3e49a6df155be6b2e29b4c5a249782fcc68517285156b19312af
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 27/27
scenarios: 0/0
test_command: cd backend && ./mvnw verify
test_exit_code: 0
test_output_hash: sha256:384c40c90307b3172e34c91b7430f5f554ec531db560892565aeb0df6191cc1d
build_command: cd backend && ./mvnw verify
build_exit_code: 0
build_output_hash: sha256:384c40c90307b3172e34c91b7430f5f554ec531db560892565aeb0df6191cc1d
```

# Verification Report — `add-analytics-module`

**Change**: `add-analytics-module` (branch `feat/ep-07-analytics-03-s3-verification`)
**Commits**: S1 `042b995` · S2 `892d723` · S3 `f52732b`
**Mode**: full artifacts (contract + design + tasks), source inspection plus real execution evidence
**Verdict**: **PASS WITH WARNINGS** — one task checkbox (2.7) left unticked; its substance is
independently re-confirmed by this phase. No CRITICAL, no blocker. Ready for `sdd-archive` once the box
is ticked.

---

## 1. Task completeness

40 boxes in `tasks.md`: 9 in S1 (1.1–1.9), 7 in S2 (2.1–2.7), 11 in S3 (3.1–3.11 — note S3 is
numbered `3.1`…`3.11`, i.e. 11 items). **39 are `[x]`; task 2.7 is `[ ]`.**

| Slice | Boxes | State |
| :--- | :--- | :--- |
| S1 — `shared` port + `analytics` domain/application | 1.1–1.9 | all `[x]`, code present |
| S2 — infrastructure + web | 2.1–2.6 `[x]`, **2.7 `[ ]`** | code present; 2.7 is a verification checklist, see below |
| S3 — cross-cutting verification + docs | 3.1–3.11 | all `[x]`, ITs present and green |

**Task 2.7 (unchecked)** — "Verify `rg @Transactional analytics` shows `readOnly = true` everywhere;
run one endpoint with SQL logging to confirm `JdbcClient` joins that transaction; every §7 code
reachable; `validar_esquema.sh` green and unaffected; `./mvnw verify`." Four of its five sub-items are
re-confirmed here directly (see §2, §5, §10). The fifth — an SQL-logging spot-check that `JdbcClient`
enlists in the `readOnly = true` transaction (`design §3`, the "one claim left to execute") — was not
independently reproduced, but `AnalyticsReadOnlyIT` exercises all seven endpoints and asserts the row
counts of `sales`, `sale_items`, `branch_inventories`, `kardex_movements`, `transfers`,
`transfer_items`, `audit_logs` and `system_alerts` are byte-for-byte unchanged, and every service
method is `@Transactional(readOnly = true)`. The behavioural guarantee (R-01) is proven; only the
diagnostic checkbox is open. Classified **WARNING**, not CRITICAL — no code is missing.

No other task's `[x]` contradicts code state. Every file named by a checked task exists with the
described shape.

---

## 2. Command evidence

| Command | Result | Notes |
| :--- | :--- | :--- |
| `python3 scripts/validar_trazabilidad.py` | **RESULTADO: trazabilidad íntegra** | `43 RF · 34 RNF · 17 RN · 39 CU · 14 DT`; item 4 → `14 declarados, 14 con ficha`; 38 links, 0 broken. Exit 0. Matches the contract's pinned counts (`43 · 34 · 17 · 39`) plus the new `DT-14`. |
| `./scripts/validar_esquema.sh` | **RESULTADO: 34 comprobaciones correctas — esquema íntegro** | All A–H invariant groups green. Exit 0. Schema **unaffected** — `git diff main...HEAD -- backend/init-db/` is **0 lines**. Contract §2.5 / design §10 upheld. |
| `cd backend && ./mvnw verify` | **BUILD SUCCESS** (2:10 min) | Surefire `Tests run: 520, Failures: 0, Errors: 0, Skipped: 0`. Failsafe `Tests run: 223, Failures: 0, Errors: 0, Skipped: 0` (was 199 before this change → +24, exactly the 24 new analytics IT tests). `ModuleBoundariesTest` (5) and `SharedIsFrameworkFreeTest` (1) included and green. Exit 0. |

**Archived `*IT` after the S2 403-body change.** `IamAccessDeniedHandler` (new in S2, design D-9 / trap 7)
turns every Spring-Security matcher denial from an empty 403 into `{"code":"forbidden", …}`. All
pre-existing ITs still pass — e.g. `ExternalSaleIntakeIT` (5), `TransferApiSmokeIT` (8),
`TransferBranchIsolationIT` (7), `UserAdminIT` (15) — and the full failsafe run reports 0 failures /
0 errors. No archived test asserted on an empty 403 body.

**New analytics ITs in the failsafe run** (all green): `AnalyticsReadOnlyIT` (1),
`AnalyticsBranchIsolationIT` (6), `SalesTrendAndRotationIT` (1), `ReplenishmentPanelIT` (2),
`CorporateBoardIT` (3), `TransferStockImpactIT` (2), `ExternalAvailabilityIT` (5),
`AnalyticsApiSmokeIT` (4) = 24.

---

## 3. Definition of Done — contract §10, the three PR checklists

### PR 1 — domain + application

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| `analytics.domain` + `analytics.application` exist, no Spring/Jakarta in `domain`; `shared` network-availability port (P-03) exists; `shared` imports no module name | **MET** | `rg "org.springframework|jakarta.(persistence|validation)" analytics/domain` → nothing. `shared/availability/{NetworkAvailabilityPort,NetworkAvailabilityView,BranchAvailabilityView}.java` import only `java.util`, `java.math`. `SharedIsFrameworkFreeTest` + `ModuleBoundariesTest` green. |
| No class in a direct sub-package of `com.optiplant.inventory` other than `analytics` | **MET** | New base-package classes: none. `IamAccessDeniedHandler` lives in `iam/infrastructure/config`. `NetworkAvailabilityAdapter` in `inventory/infrastructure/adapter/out/availability`. |
| Unit `*Test` (no Docker) covering R-09 ABC boundaries, R-05 variation incl. zero-previous-month, R-10 / R-15 `coverageDays` edges, R-16 severity ordering, R-00 page-size rejection | **MET** | `AbcClassifierTest` (80.00→A, 80.01→B, 95.00→B, 95.01→C, 100→C), `CoveragePolicyTest` (7 cases incl. zero-stock→0, zero-demand→null), `SalesTrendPolicyTest` (zero-fill, null variation, empty), `AnalyticsAccessPolicyTest` (6), `ReplenishmentSeverityTest`, plus 19 service tests — all in surefire (520 green). |
| `./mvnw verify` green | **MET** | BUILD SUCCESS. |

### PR 2 — infrastructure + web

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| Native-SQL read adapters with projections; `rg "@Entity" …/analytics` → nothing (P-01) | **MET** | Five `*JdbcAdapter` under `analytics/infrastructure/adapter/out/persistence` + `ServiceUserJdbcAdapter`, all on `JdbcClient` with private row mappers. `grep -rn "@Entity" analytics` → **nothing**. |
| `rg "@Transactional" …/analytics` → `readOnly = true` on every occurrence; `rg "INSERT|UPDATE|DELETE|StockMutationPort|AuditWritePort|ApplicationEventPublisher" …/analytics` → nothing | **MET** | 7 `@Transactional` occurrences (6 services, `QueryTransferActivityService` has 2 methods) — **all `readOnly = true`**. The forbidden-token grep returns **nothing**. No `@Async` / `@EventListener` / `AFTER_COMMIT` anywhere in `analytics`. |
| Every sales aggregation filters `status = 'COMPLETED'` (F-2); none queries `kardex_movements` (F-4) | **MET** | `s.status = 'COMPLETED'` present in `SalesAnalyticsJdbcAdapter` (Q-1/Q-2/Q-3), `InventoryAnalyticsJdbcAdapter` (coverage demand sub-query) and `BranchBoardJdbcAdapter` (Q-7 `month_sales` CTE). `kardex_movements` appears only in a Javadoc comment — **no query names it**. |
| Controllers, exception handler, `SecurityConfig` matchers for `/api/analytics/**` with `hasAuthority()`; `analytics` API-key chain for `/api/external/availability/**` with a non-shadowing `@Order` | **MET** | `AnalyticsDashboardController`, `CorporateBoardController`, `ExternalAvailabilityController`, `AnalyticsExceptionHandler` (`@RestControllerAdvice(basePackages = "…analytics.infrastructure.adapter.in")`). `SecurityConfig`: `/api/analytics/corporate/**` → `hasAuthority("ADMIN")` **before** `/api/analytics/**` → `authenticated()`, string literals only. `ExternalAvailabilitySecurityConfig` `@Bean @Order(2)` with exact `securityMatcher("/api/external/availability/**")` (sales holds `@Order(1)` for `/api/external/sales/**`; two exact matchers cannot shadow). |
| `inventory` implements the P-03 port; `analytics` consumes it and implements nothing | **MET** | `NetworkAvailabilityAdapter` (`@Component`) in `inventory`, the single implementation, maps `NetworkAvailability → NetworkAvailabilityView`, drops `isOwnBranch`, delegates to `QueryStockUseCase.networkAvailability(UUID)` (D-3 overload). `analytics` injects `NetworkAvailabilityPort` straight into `QueryNetworkAvailabilityService`; declares no adapter. |
| Every §7 code reachable from ≥1 controller path — no dead code | **MET** | See §5 — all 7 codes mapped and live. |
| `./scripts/validar_esquema.sh` green and unaffected | **MET** | 34 checks green; `backend/init-db/` diff empty. |
| `./mvnw verify` green | **MET** | BUILD SUCCESS. |

### PR 3 — verification

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| `AnalyticsReadOnlyIT` — R-01: all seven endpoints, row counts of `sales`, `sale_items`, `branch_inventories`, `kardex_movements`, `transfers`, `transfer_items`, `audit_logs`, `system_alerts` unchanged | **MET** | Present, 1 test green. Counts asserted before/after all 7 calls. |
| `AnalyticsBranchIsolationIT` — R-02/§5: A never sees B; `BRANCH_MANAGER` + `branchExternalId` → `403 cross_branch_access_denied`; corporate `ADMIN` → `403 branch_context_required` without it, real data with it; `BRANCH_MANAGER` on corporate board → `403` with `{code,message}` body | **MET** | Present, 6 tests green. |
| `SalesTrendAndRotationIT` — R-03/R-04/R-08/R-09: real-PostgreSQL figures; voided sale drops out; ABC stable across pages | **MET** | Present, 1 test green. |
| `ReplenishmentPanelIT` — R-15/R-16/R-18: column-to-column threshold against real PostgreSQL (F-3), `OUT_OF_STOCK` first, empty branch → empty page | **MET** | Present, 2 tests green. |
| `ExternalAvailabilityIT` — R-23/R-24/R-25/R-26: API-key path returns exactly the CU-INV-04 payload; absent/unknown keys → `401 invalid_api_credential`; unknown product → `404`; zero-stock → explicit zeroed result; `/api/external/sales` still authenticates (trap 5) | **MET** | Present, 5 tests green. |
| Smoke coverage of the remaining read endpoints (status, envelope shape, no numeric `id`, no forbidden cost field) | **MET** | `AnalyticsApiSmokeIT` (4) + `CorporateBoardIT` (3) + `TransferStockImpactIT` (2) — the tasks file expanded contract §10's IT list with these three named classes (see WARNING 1). |
| `DT-14` written into `docs/deuda_tecnica.md` — summary row **and** detail card, Spanish, with the §9.2 pay-off plan; no `backend/init-db/` change | **MET** | Changelog `1.8` (`:14`), registry row `DT-14 … Media … Aceptada` (`:57`), detail card after `DT-13` (`:438`) with Situación actual / Por qué se aceptó / Por qué es deuda / Plan de pago (the two verbatim `CREATE INDEX … INCLUDE` statements + nightly-rollup fallback) / Referencias. `init-db/` diff empty. |
| `python3 scripts/validar_trazabilidad.py` green — 14 DT declared, 14 with fiche | **MET** | Item 4 → `14 declarados, 14 con ficha`; RF/RNF/RN/CU unchanged at `43 · 34 · 17 · 39`. |
| `./mvnw verify` green, `ModuleBoundariesTest` included | **MET** | BUILD SUCCESS; `ModuleBoundariesTest` ran (5 green). |

---

## 4. Behavioural contract — §4, R-00 … R-26 traced to code

Traced against production code, not only tests.

- **R-00 / RNF-PER-04** — `AnalyticsDashboardController.resolveSize` and `CorporateBoardController.resolveSize` throw `IllegalArgumentException` (→ `400 invalid_request`) for `size < 1 || size > 100`; never clamped. `page < 0` → `Math.max(page, 0)`. No endpoint takes the acting branch from a non-`ADMIN` caller (`AnalyticsAccessPolicy` step 1).
- **R-01 / P-01 / P-02** — no `@Entity`, no write port, no `INSERT/UPDATE/DELETE`, no `ApplicationEventPublisher`, no `@Async` in `analytics` (greps all empty). `AnalyticsReadOnlyIT` proves eight tables + `system_alerts` unchanged after all seven endpoints.
- **R-02 / PA-01** — `AnalyticsAccessPolicy.resolveBranch` implements the five ordered steps verbatim: non-`ADMIN` + `branchExternalId` → `CrossBranchAccessDeniedException` **before any lookup**; non-`ADMIN` → `actor.branchId()`; `ADMIN` + requested → requested (then `BranchDirectoryPort.isActiveBranch` → `BranchNotFoundException` / `404` in each service); branch-assigned `ADMIN` without it → own branch; corporate `ADMIN` (`branchId == null`) without it → `BranchContextRequiredException` / `403 branch_context_required`. The four branch dashboards call it; the corporate board and CU-EXT-01 do not.
- **R-03** — every `sales` aggregation carries `s.status = 'COMPLETED'`. `SalesTrendAndRotationIT` voids a sale and re-reads: every figure decreases.
- **R-04 / R-05 / R-06 / R-07** — `SalesTrendPolicy` zero-fills every month in the window, computes `monthOverMonthVariationPercent` (`null` when the previous month is zero, never `100`, never div-by-zero) and the `empty` marker; `AnalyticsDashboardController.salesTrend` rejects `months` outside `1…12` with `400`.
- **R-08 / R-09 / D-5 / D-6** — `SalesAnalyticsJdbcAdapter.rotation` computes cumulative share with `SUM(...) OVER (ORDER BY salesAmount DESC, p.sku ASC)` in a CTE with **no `LIMIT` inside** (paging is `LIMIT/OFFSET` outside); `AbcClassifier.classify` owns the 80 %/95 % cut-points in Java, applied over the whole ranked period by `RotationPageAssembler`. `direction=BOTTOM` flips only the outer `ORDER BY`. `SalesTrendAndRotationIT` pins classes identical across two pages.
- **R-10 / R-15 / D-7** — `CoveragePolicy` is the single function for rotation and replenishment: `0` when stock is zero, `null` when demand is zero (never infinity), else `currentStock ÷ (unitsSold ÷ periodDays)` at scale 2.
- **R-11** — `QueryProductRotationService` throws `IllegalArgumentException` (→ `400`) when `from` is after `to` or the window exceeds 366 days; `from`/`to` default to the current calendar month.
- **R-12 / R-13 / R-14** — `TransferAnalyticsJdbcAdapter` counts `REQUESTED`/`IN_PREPARATION`/`IN_TRANSIT` split inbound/outbound plus delayed (`estimated_arrival_at < now() AND actual_arrival_at IS NULL`); stock impact = `SUM(dispatched_quantity)` of `IN_TRANSIT` inbound, `SUM(requested_quantity)` of `REQUESTED`/`IN_PREPARATION` outbound, `projectedStock = currentStock + inboundInTransit − outboundCommitted`; `in_transit_stock` reported as stored. `TransferStockImpactIT` pins exact figures.
- **R-15 / R-16 / D-8** — `InventoryAnalyticsJdbcAdapter.replenishment` filters `p.is_active` + `bi.current_stock <= bi.min_stock_threshold`, default `ORDER BY CASE WHEN current_stock = 0 THEN 0 ELSE 1 END, p.sku`; `ReplenishmentSeverity.of` returns `OUT_OF_STOCK` at zero, `CRITICAL` otherwise. `ReplenishmentPanelIT` pins SQL order == Java rule.
- **R-17** — `ReplenishmentLineResponse` carries `productExternalId`; no purchase-order / transfer-request creation anywhere in the module.
- **R-18** — `ReplenishmentPanelIT`: a branch with nothing below threshold → `200` empty page, never `404`.
- **R-19** — `/api/analytics/corporate/**` → `hasAuthority("ADMIN")` matcher; `IamAccessDeniedHandler` renders `403 {"code":"forbidden"}`. `AnalyticsBranchIsolationIT` proves `BRANCH_MANAGER` → `403` with body.
- **R-20 / R-21 / R-22 / F-8** — `BranchBoardJdbcAdapter.corporateBoard`: one row per `b.is_active = TRUE` branch, `salesAmount`/`salesCount`/`unitsSold` over `COMPLETED` sales, `inventoryValue = SUM(current_stock × average_cost)`, `criticalProductCount`, `activeTransferCount`; whitelist sort over all six indicators + `code`/`name`, unknown `sort`/`direction` → `IllegalArgumentException` / `400` (no silent fallback). `CorporateBoardIT` pins exact figures + both sort directions + `400` on unknown key.
- **R-23 / R-24 / R-26 / D-1 / D-2 / D-3** — `QueryNetworkAvailabilityService` calls `NetworkAvailabilityPort.networkAvailability(UUID)` and maps `Optional.empty()` → its own `ProductNotFoundException` (`404`). `StockQueryService.networkAvailability(UUID)` is the actor-free overload; the actor-taking method now delegates to it and `mark`s — behaviour byte-identical, zero logic restated. `NetworkAvailabilityView` / `BranchAvailabilityView` have **no `isOwnBranch`** field; `ExternalNetworkAvailabilityResponse` exposes only `external_id`, `sku`, `name`, quantities, `networkTotal` — no cost/`average_cost`/price. `ExternalAvailabilityIT` asserts the zeroed-result and no-cost cases.
- **R-25** — `ExternalAvailabilityApiKeyFilter`: absent, blank, unmatched, or matched-to-inactive-user all write the identical `401 {"code":"invalid_api_credential"}` body; `findMatchingEntry` iterates every entry with `MessageDigest.isEqual` (no early return, no position leak); nothing logged. `shouldNotFilter` returns `true` unless the URI starts with `/api/external/availability` (trap 5).

---

## 5. Error taxonomy — §7, every code reachable

All 7 codes are mapped and each has a live throw site.

| Code | HTTP | Throw site |
| :--- | :---: | :--- |
| `invalid_request` | 400 | `AnalyticsExceptionHandler.onIllegalArgument` / `onBeanValidation` / `onTypeMismatch` — `resolveSize` out of range, `months` ∉ 1–12, `from` after `to` / window > 366 d, unknown `direction` / `sort` / `severity`, unknown corporate `sort`/`direction` in `BranchBoardJdbcAdapter`, malformed UUID |
| `invalid_api_credential` | 401 | `ExternalAvailabilityApiKeyFilter.writeUnauthorized` — absent / blank / unknown key, inactive service user |
| `forbidden` | 403 | `IamAccessDeniedHandler` — non-`ADMIN` denied by the `/api/analytics/corporate/**` matcher (R-19) |
| `branch_context_required` | 403 | `AnalyticsAccessPolicy` step 5 → `BranchContextRequiredException` — corporate `ADMIN` on a branch dashboard with no `branchExternalId` |
| `cross_branch_access_denied` | 403 | `AnalyticsAccessPolicy` step 1 → `CrossBranchAccessDeniedException` — `BRANCH_MANAGER`/`OPERATOR` sending `branchExternalId` |
| `branch_not_found` | 404 | `QuerySalesTrendService` / `QueryProductRotationService` / `QueryTransferActivityService` / `QueryReplenishmentService` → `BranchNotFoundException` when `ADMIN` names an inactive/unknown branch (`BranchDirectoryPort.isActiveBranch` false) |
| `product_not_found` | 404 | `QueryNetworkAvailabilityService` → `ProductNotFoundException` on `Optional.empty()` from the P-03 port |

No leaked numeric `id`, stack trace, SQL text, constraint/index name, or API-key material found in any
inspected response path. Non-`ADMIN` gets `cross_branch_access_denied` **before** any branch lookup, so
no probe distinguishes a real branch from an invented one. `inventoryValue` appears only on the
corporate-board response.

---

## 6. Authorization matrix — §5 vs `SecurityConfig`

`iam/infrastructure/config/SecurityConfig`, after the `purchases` block and before
`anyRequest().authenticated()`:

```
1. /api/analytics/corporate/**   -> hasAuthority("ADMIN")     // R-19
2. /api/analytics/**             -> authenticated()            // §5: all three roles
```

Corporate matcher first (or rule 2 swallows it). String literals only — no `analytics` type imported
into `iam` (`ModuleBoundariesTest` green is the backstop). `hasAuthority` only, never `hasRole`, so no
`ROLE_` prefix. `/api/external/availability/**` is not in this chain — it has its own
`@Order(2)` `SecurityFilterChain` with an exact `securityMatcher`, `STATELESS`, API-key filter before
`UsernamePasswordAuthenticationFilter`. Branch scope for the four dashboards is resolved in
`AnalyticsAccessPolicy` from `AuthenticatedPrincipal.branchId`, never a parameter (except the
`ADMIN`-only `branchExternalId`). Matches §5 exactly, including the corporate-`ADMIN` (`branchId == null`)
rows.

---

## 7. Invariants that broke this project before

| Invariant | Status | Evidence |
| :--- | :--- | :--- |
| Roles `ADMIN`/`BRANCH_MANAGER`/`OPERATOR`, no `ROLE_` prefix, `hasAuthority()` not `hasRole()` | **HELD** | `SecurityConfig` analytics matchers use `hasAuthority("ADMIN")`; `AnalyticsAccessPolicy` compares `actor.role() != Role.ADMIN`. No `hasRole` anywhere in the change. |
| Every stock mutation writes its Kardex row in the same transaction | **N/A — HELD by omission** | `analytics` performs zero mutations (R-01). No `applyMovement`, no Kardex write. |
| Atomic effects go through a synchronous outbound port, never an event | **HELD** | The P-03 port call is a plain synchronous read inside the `readOnly = true` transaction. No `@Async`, no domain event, no `AFTER_COMMIT` in `analytics` (T-04/T-06). |
| Branch derived from the authenticated session, never a client parameter | **HELD** | `AnalyticsAccessPolicy.resolveBranch(actor, …)`; `branchExternalId` honoured only for `ADMIN` (RN-08 cross-branch **reads**), rejected with `403` for other roles — never silently ignored. |
| API exposes only `external_id`, never numeric `id` | **HELD** | Every controller response record types identifiers as `UUID` from `external_id` columns. JDBC adapters use `s.id` / `p.id` / `b.id` only for joins, `GROUP BY` and `COUNT(...)` — never in a projection returned to a mapper. `AnalyticsApiSmokeIT` asserts no numeric id in payloads. |
| No class in a direct sub-package of `com.optiplant.inventory` that is not a business module | **HELD** | All new classes under `analytics.*`, `shared.availability`, `inventory.infrastructure…`, `iam.infrastructure.config`. `ModuleBoundariesTest` green. |
| `shared/` is an open module and must be a leaf | **HELD** | `shared/availability/*` imports only `java.util` / `java.math`. `SharedIsFrameworkFreeTest` + `ModuleBoundariesTest.sharedEsUnaHoja` green. |
| `*IT` (not `*Test`) for Docker-needing tests | **HELD** | All 8 new Docker classes end in `IT` (failsafe); all 24 new unit classes end in `Test` (surefire). |
| No Flyway alongside `backend/init-db/`; zero schema change | **HELD** | `git diff main...HEAD -- backend/init-db/` empty; no Flyway dependency added; `validar_esquema.sh` green and unaffected. |

---

## 8. PA / D decisions — spot-checks

| Decision | Status | Evidence |
| :--- | :--- | :--- |
| **PA-01 / R-02** — corporate `ADMIN` names `branchExternalId`; omitting it → `403 branch_context_required` pointing at the corporate board | **AS WRITTEN** | `AnalyticsAccessPolicy` steps 3–5; `BranchContextRequiredException` message names `/api/analytics/corporate/branches`. `AnalyticsBranchIsolationIT` proves both paths. |
| **PA-02 / D-5** — ABC thresholds 80 %/95 % of cumulative amount, classified over the whole period, cut-points in Java | **AS WRITTEN** | `AbcClassifier.classify` (pure), `SUM(...) OVER (ORDER BY salesAmount DESC, p.sku ASC)` CTE with no inner `LIMIT`. |
| **PA-03 / F-5 / D-7** — rotation reported as `unitsSold` + `coverageDays`, no turnover ratio | **AS WRITTEN** | `CoveragePolicy` is the only derivation; no ratio anywhere; no historical-snapshot table read. |
| **PA-05 / F-6** — `analytics` duplicates the API-key filter, imports nothing from `sales` | **AS WRITTEN** | `ExternalAvailabilityApiKeyProperties/Filter/Token/SecurityConfig` + `ServiceUserPort`/`ServiceUserJdbcAdapter` under `analytics/infrastructure/config`; `grep -rn "com.optiplant.inventory.sales" analytics` → nothing. Prefix `optiplant.analytics.external`, no `branchExternalId` in the entry (network-wide, principal `branchId == null`). |
| **PA-06 / F-4 / A-7** — rotation/demand from `sales`/`sale_items`, never `kardex_movements` | **AS WRITTEN** | `kardex_movements` appears only in a Javadoc comment in `SalesAnalyticsJdbcAdapter`; no query references it. |
| **D-3** — `QueryStockUseCase` gains actor-free `networkAvailability(UUID)`; actor method `mark`s over it | **AS WRITTEN** | `git diff` shows exactly that refactor; `+13 / -6` in `StockQueryService`, `+9` in the port. Existing `inventory` ITs green — behaviour preserved. |
| **D-4** — `analytics` reads with `JdbcClient`, not Spring Data | **AS WRITTEN** | All six adapters inject `JdbcClient`; no `Repository` / `@Entity` / `EntityManager` in `analytics`. |
| **D-9 / trap 7** — corporate `ADMIN` gate is a matcher; `iam` gains an `AccessDeniedHandler` | **AS WRITTEN** | `IamAccessDeniedHandler` (`@Component`) wired via `.exceptionHandling(e -> e.accessDeniedHandler(...))`; emits `{"code":"forbidden","message":"Access denied"}`. All archived ITs still green. |
| **D-11** — external chain is `@Order(2)` with an exact `securityMatcher` | **AS WRITTEN** | `ExternalAvailabilitySecurityConfig` `@Bean @Order(2)`, `securityMatcher("/api/external/availability/**")`; `sales` holds `@Order(1)`. `ExternalAvailabilityIT` asserts `/api/external/sales` still authenticates with its own key. |

---

## 9. Zero schema change

`git diff main...HEAD -- backend/init-db/` is **empty**. `./scripts/validar_esquema.sh` → 34 checks
green, schema intact. No `01-init-schema.sql`, `02-seed-data.sql` or `docs/diagrama_er.md` edit; no
index added (F-3, F-4, A-1 — deferred to `DT-14`). Contract §2.5 / design §10 upheld.

---

## 10. Read-only proof (R-01, P-01, P-02, T-01)

| Check | Command | Result |
| :--- | :--- | :--- |
| No JPA entity | `grep -rn "@Entity" …/analytics` | nothing |
| Every transaction read-only | `grep -rn "@Transactional" …/analytics` | 7 hits, **all `readOnly = true`** |
| No writes / write ports / events | `grep -rniE "INSERT|UPDATE|DELETE|StockMutationPort|AuditWritePort|ApplicationEventPublisher" …/analytics` | nothing |
| No async / commit hooks | `grep -rnE "@Async|@EventListener|AFTER_COMMIT|TransactionalEventListener" …/analytics` | nothing |
| No Kardex read | `grep -rn "kardex_movements" …/analytics` | one Javadoc comment only |
| No write HTTP verbs | `grep -rnE "@(Post|Put|Patch|Delete)Mapping" …/analytics` | nothing |
| Runtime | `AnalyticsReadOnlyIT` | 8 table counts + `system_alerts` unchanged after all 7 endpoints |

`JdbcClient`-joins-the-`readOnly`-transaction was **not** independently reproduced with SQL logging
(task 2.7's open sub-item), but the write-count assertions above make the behavioural guarantee
solid regardless.

---

## 11. No foreign files

`git diff --name-status main...HEAD` touches, and only touches:

- `backend/src/main/java/com/optiplant/inventory/analytics/**` — 48 new production classes
- `backend/src/main/java/com/optiplant/inventory/shared/availability/**` — 3 new classes (P-03 port + 2 views)
- `inventory`: `QueryStockUseCase.java` (M), `StockQueryService.java` (M), `NetworkAvailabilityAdapter.java` (new)
- `iam/infrastructure/config`: `SecurityConfig.java` (M), `IamAccessDeniedHandler.java` (new)
- `backend/src/main/resources/application-dev.yml` (M — 5 lines, the external API-key entry)
- `backend/src/test/java/com/optiplant/inventory/{AnalyticsApiSmokeIT,AnalyticsBranchIsolationIT,AnalyticsReadOnlyIT,CorporateBoardIT,ExternalAvailabilityIT,ReplenishmentPanelIT,SalesTrendAndRotationIT,TransferStockImpactIT}.java` (new)
- `backend/src/test/java/com/optiplant/inventory/analytics/**` — 11 new unit-test classes
- `docs/deuda_tecnica.md` (M — `DT-14`), `openspec/PLAN.md` (M — 10/10, 39/39), `openspec/changes/add-analytics-module/**`

Nothing under `frontend/`. `TestcontainersConfiguration.java` untouched. `SecurityConfig.java` and
`IamAccessDeniedHandler.java` are mandated by design §6 D-9 / task 2.6 — expected, not stray.

---

## Issues found

**CRITICAL: none.**

**WARNING (2):**

1. **Task 2.7 is unchecked (`[ ]`).** Its five sub-items: (a) `@Transactional` all `readOnly = true` —
   re-confirmed here; (b) SQL-logging spot-check that `JdbcClient` enlists in the read-only
   transaction — **not independently reproduced**; (c) every §7 code reachable — re-confirmed (§5);
   (d) `validar_esquema.sh` green and unaffected — re-confirmed; (e) `./mvnw verify` — re-confirmed.
   Only (b), a diagnostic, is genuinely open; R-01 is proven by `AnalyticsReadOnlyIT` regardless. Tick
   the box (optionally after a one-shot `spring.jpa.show-sql` run against one endpoint) before archive.
2. **The tasks file expanded contract §10 PR 3's fixed IT list.** Contract §10 PR 3 says "the list is
   fixed … add none, drop none" and names five IT classes plus "smoke coverage". `tasks.md` 3.5 / 3.6
   add `CorporateBoardIT` and `TransferStockImpactIT` as named classes, and 3.8 names
   `AnalyticsApiSmokeIT`. This is strictly *more* verification (all three are green) and every original
   contract IT is present, so it strengthens rather than weakens the change — but it is a deviation
   from the contract's "add none" wording. Non-blocking.

**SUGGESTIONS (3):**

1. **Task 2.4 "add the keys to … the test profile."** No shared test YAML entry was added; instead each
   IT declares its own `@SpringBootTest(properties = {"optiplant.analytics.external.api-keys[0]…"})`.
   Functionally equivalent and arguably cleaner (no shared secret), but not the literal task wording.
2. **`design.md` names the handler `AnalyticsExceptionHandler` living in `iam` (§9 line "iam gains an
   AccessDeniedHandler").** The implementation is `IamAccessDeniedHandler` in `iam/infrastructure/config`
   — the right package and role; only the working name in the prose differs.
3. **`application-dev.yml` hard-codes `user-external-id: e0000000-0000-0000-0000-000000000005`.** Fine
   for a dev profile pointing at a seeded service user, but worth a comment noting which seed row it
   binds to, mirroring how `sales` documents its external key.

---

## Final verdict

**PASS WITH WARNINGS** — 39 of 40 task boxes complete and matching code state; the one open box (2.7)
is a verification checklist whose substance is re-confirmed by this phase and whose behavioural
guarantee (R-01) is proven by `AnalyticsReadOnlyIT`. All three contract §10 PR checklists satisfied in
committed code; every `R-00 … R-26` traced to a controller/service/domain/SQL path; all 7 §7 error
codes reachable; the §5 matrix matches `SecurityConfig`; `analytics` is verifiably read-only (no
`@Entity`, every `@Transactional` `readOnly = true`, no write tokens, no Kardex read, no write verbs,
row counts unchanged at runtime); `shared/availability` is a JDK-only leaf; no `analytics → inventory`
or `analytics → sales` compile edge (`ModuleBoundariesTest` green); API exposes only `external_id`;
corporate board is `ADMIN`-only; PA-01 handled per contract. Three gates green —
`validar_trazabilidad.py` (`43 RF · 34 RNF · 17 RN · 39 CU · 14 DT`, 14/14 fiches),
`validar_esquema.sh` (34 checks, schema unaffected, `init-db/` diff empty),
`cd backend && ./mvnw verify` (**BUILD SUCCESS**, surefire 520, failsafe 223, `ModuleBoundariesTest`
included). `openspec/PLAN.md` at 10/10 modules and 39/39 use cases. No foreign files, nothing under
`frontend/`. No CRITICAL, no blocker: ready for `sdd-archive` (tick box 2.7 first).
