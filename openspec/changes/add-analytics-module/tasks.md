# Tasks: `add-analytics-module`

Three slices, one PR each, matching `contract.md` §10 and `openspec/PLAN.md` §3; `design.md` is cited by
section. **Zero schema change and zero index** — if a task seems to need one, §2.5 was wrong: stop and report.
`rg "@Entity|INSERT|UPDATE|DELETE|StockMutationPort|AuditWritePort|ApplicationEventPublisher"` over `analytics`
must return **nothing** after every phase (R-01, P-01, P-02). The domain layer is deliberately thin — records
and five pure functions (design §5); do not invent value objects to pad it.

## Phase 1 — S1: `shared` port, domain and application (PR1)

- [x] 1.1 Create `shared/availability/`: `NetworkAvailabilityPort`, `NetworkAvailabilityView` and
      `BranchAvailabilityView` (design §2, P-03) — **no `isOwnBranch`** (R-24); `shared` imports no module name.
- [x] 1.2 Edit `inventory/application/port/in/QueryStockUseCase` + `application/service/StockQueryService` —
      add the actor-free `NetworkAvailability networkAvailability(UUID)`; the actor-taking method becomes
      `mark(...)` over it, behaviour byte-identical (D-3). No adapter, mapper, entity or repository edit.
- [x] 1.3 Create `analytics/domain/model/` (design §5) — records only, no setters, no `with*`: `MonthlySales`,
      `SalesTrend`, `RotationLine`, `AbcClass`, `RotationDirection`, `TransferStatusCounts`,
      `TransferActivitySummary`, `TransferStockImpact`, `ReplenishmentLine`, `ReplenishmentSeverity`
      (`of(currentStock)`), `BranchPerformance`, `AnalyticsPeriod`, `AnalyticsPage<T>`.
- [x] 1.4 Create `analytics/domain/service/` (design §4–§6): `AbcClassifier` (R-09 cut-points),
      `CoveragePolicy` (R-10/R-15, three outcomes incl. `null` for zero demand), `SalesTrendPolicy` (R-04
      zero-fill, R-05 `null` variation, R-06 `empty`), `RotationPageAssembler`, `AnalyticsAccessPolicy` (R-02's
      five ordered steps, refusal **before** any lookup); plus `domain/exception/`'s four types.
- [x] 1.5 Create `analytics/application/port/out/`: `SalesAnalyticsPort`, `InventoryAnalyticsPort`,
      `TransferAnalyticsPort`, `BranchBoardPort`, `BranchDirectoryPort` (design §8) — every method returns a
      value and **none takes a command or returns void** (P-02).
- [x] 1.6 Create `analytics/application/port/in/` and `service/` — design §8's six use cases and six
      services, each method `@Transactional(readOnly = true)` (T-01). The availability service injects
      `shared`'s `NetworkAvailabilityPort` directly and maps `Optional.empty()` to its own
      `ProductNotFoundException` (D-2). No branch reaches a query except via `AnalyticsAccessPolicy`.
- [x] 1.7 Unit `AbcClassifierTest` (R-09: exactly 80 %, exactly 95 %, one product at 100 %, either side of each
      boundary), `CoveragePolicyTest` (R-10/R-15: zero stock ⇒ `0`, zero demand ⇒ `null`, never infinity),
      `SalesTrendPolicyTest` (R-04 zero-filled months, R-05 zero previous month ⇒ `null` not `100`, R-06 `empty`).
- [x] 1.8 Unit `AnalyticsAccessPolicyTest` (R-02): non-`ADMIN` sending `branchExternalId` ⇒
      `cross_branch_access_denied` **before** any port call (assert the stub is untouched); non-`ADMIN` ⇒ own
      branch; `ADMIN` naming one ⇒ that branch; branch-assigned `ADMIN` omitting it ⇒ own branch; corporate
      `ADMIN` omitting it ⇒ `branch_context_required`.
- [x] 1.9 Unit service tests with stubbed ports — `abcClass` identical for the same product across two
      pages (R-09); `BOTTOM` reverses presentation without changing a class (D-6); no stub is asked to write.
      Then `rg "org\.springframework|jakarta\.persistence" analytics/domain` returns nothing and
      `cd backend && ./mvnw verify` is green. **Ship the six services unannotated** while their out-ports
      have no adapter — 2.5 restores `@Service`; registering them now breaks `ApplicationContextIT` (trap 6).

## Phase 2 — S2: infrastructure and web (PR2)

- [x] 2.1 Create `inventory/infrastructure/adapter/out/availability/NetworkAvailabilityAdapter` (`@Component`) —
      the single `NetworkAvailabilityPort` implementation over 1.2's overload, dropping `isOwnBranch` (design §2).
- [x] 2.2 Create `analytics/infrastructure/adapter/out/persistence/SalesAnalyticsJdbcAdapter` — design §4's
      Q-1, Q-2, Q-3 over `JdbcClient` (D-4). Q-2's cumulative share is `SUM(...) OVER (ORDER BY salesAmount
      DESC, p.sku ASC)` in a CTE with **no `LIMIT` inside it** (trap 4); paging is `LIMIT/OFFSET` outside.
      Every statement carries `s.status = 'COMPLETED'` (F-2), joining `sale_items` via `sales.id` (F-1).
- [x] 2.3 Create the three remaining read adapters — `InventoryAnalyticsJdbcAdapter` (Q-4 — column-to-column
      threshold, `p.is_active`, D-8's `ORDER BY CASE` default), `TransferAnalyticsJdbcAdapter` (Q-5, Q-6 —
      `dispatched_quantity` inbound `IN_TRANSIT`, `requested_quantity` outbound
      `REQUESTED`/`IN_PREPARATION`; `in_transit_stock` reported
      as stored, R-14), `BranchBoardJdbcAdapter` (Q-7 — `b.is_active`, `average_cost` for `inventoryValue`,
      R-20/R-22/F-8) and `BranchDirectoryJdbcAdapter` (Q-8). Grep-verify none mentions `kardex_movements`
      (F-4) or returns a numeric `id`.
- [x] 2.4 Create `analytics/infrastructure/config/` (design §7): `ExternalAvailabilityApiKeyProperties`
      (`optiplant.analytics.external`, constant-time match, **no `branchExternalId`**),
      `ExternalAvailabilityAuthenticationToken`, `ServiceUserPort` + its `JdbcClient` adapter (Q-9),
      `ExternalAvailabilityApiKeyFilter` (`shouldNotFilter` guarding `/api/external/availability` — trap 5; one
      `401 invalid_api_credential` body for every failure, no key material logged, R-25) and
      `ExternalAvailabilitySecurityConfig` (`@Bean @Order(2)`, exact `securityMatcher`, D-11). Add the keys to
      `application-dev.yml` and the test profile.
- [x] 2.5 Create `AnalyticsDashboardController`, `CorporateBoardController`, `ExternalAvailabilityController`
      and `AnalyticsExceptionHandler` (design §7) — contract §6's seven operations verbatim, `resolveSize`
      copied from `TransferController:153-161` (**rejects**, never clamps — R-00/DT-10), `months` 1–12
      (R-07), the date window validated (R-11), unknown `sort`/`direction` ⇒ `400` never a silent fallback
      (R-21), no numeric id, **no `POST`/`PUT`/`PATCH`/`DELETE` mapping**. Restore `@Service` on S1's services.
- [x] 2.6 Edit `iam/.../config/SecurityConfig` — design §7's two matchers, `/api/analytics/corporate/**`
      **before** `/api/analytics/**`, string literals only, `hasAuthority` never `hasRole`; plus D-9's
      `AccessDeniedHandler` emitting `{"code":"forbidden", …}` via `.exceptionHandling(...)`. **First grep
      the archived `*IT` for an assertion on an empty 403 body** (trap 7).
- [ ] 2.7 Verify `rg "@Transactional" analytics` shows `readOnly = true` everywhere (T-01); run one endpoint
      with SQL logging to confirm `JdbcClient` joins that transaction (design §3 — the one claim left to
      execute); every §7 code reachable from a controller path (name it per code in the PR description);
      `./scripts/validar_esquema.sh` green **and unaffected** (§2.5); `./mvnw verify`.

## Phase 3 — S3: cross-cutting verification and documentation (PR3)

**Docker-needing classes end in `IT`, never `Test`;** the list is fixed by contract §10 PR 3 — add none, drop
none. Each indicator IT asserts **exact figures against known seeded data**.

- [x] 3.1 `AnalyticsReadOnlyIT` — R-01: after exercising all seven endpoints, row counts of `sales`, `sale_items`,
      `branch_inventories`, `kardex_movements`, `transfers`, `audit_logs` and `system_alerts` are unchanged.
- [x] 3.2 `AnalyticsBranchIsolationIT` — R-02/§5: branch A never sees branch B's figures; `BRANCH_MANAGER`
      sending `branchExternalId` ⇒ `403 cross_branch_access_denied`; corporate `ADMIN` ⇒ `403
      branch_context_required` without it and real data with it; unknown branch ⇒ `404 branch_not_found`;
      `BRANCH_MANAGER` on the corporate board ⇒ `403 forbidden` **with the `{code, message}` body** (D-9).
- [x] 3.3 `SalesTrendAndRotationIT` — R-03/R-04/R-05/R-08/R-09/R-10: exact `salesCount`, `unitsSold`,
      `totalAmount` and `monthOverMonthVariationPercent` per month; exact `sharePercent`,
      `cumulativeSharePercent`, `abcClass` per product; a voided sale drops out of every figure; classes
      identical across two pages; `BOTTOM` reverses order without changing a class.
- [x] 3.4 `ReplenishmentPanelIT` — R-15/R-16/R-18: the column-to-column threshold returns exactly the right
      rows against real PostgreSQL (F-3), `OUT_OF_STOCK` first, exact `coverageDays`, and a branch with
      nothing below threshold answers an empty page.
- [x] 3.5 `CorporateBoardIT` — R-20/R-21/R-22: one row per **active** branch with exact `salesAmount`,
      `unitsSold`, `inventoryValue` (Σ `current_stock × average_cost`), `criticalProductCount`,
      `activeTransferCount`; every indicator sorts both ways; unknown sort key ⇒ `400 invalid_request`.
- [x] 3.6 `TransferStockImpactIT` — R-12/R-13/R-14: exact inbound/outbound/delayed counts,
      `projectedStock = currentStock + inboundInTransit − outboundCommitted`, `inTransitStock` as stored.
- [x] 3.7 `ExternalAvailabilityIT` — R-23/R-24/R-25/R-26: valid key ⇒ `200` with exactly the CU-INV-04
      payload, **`external_id` only, no `isOwnBranch`, no cost/`average_cost`/price**; absent, malformed and
      unknown keys ⇒ `401 invalid_api_credential`; unknown product ⇒ `404`; zero-stock product ⇒ an explicit
      zeroed result. Assert `/api/external/sales` still authenticates with its own key (trap 5).
- [x] 3.8 `AnalyticsApiSmokeIT` — the remaining reads: status, page-envelope shape, oversized `size` ⇒ `400
      invalid_request` (R-00), `months` outside 1–12 and an inverted range ⇒ `400` (R-07/R-11), no numeric `id`
      anywhere, no `inventoryValue` on any non-corporate response.
- [x] 3.9 Measure A-4, A-5, A-6 with `EXPLAIN (ANALYZE, BUFFERS)` against the Testcontainers PostgreSQL,
      recording the plans in the PR description (design §10) — Q-1/Q-2 drive from `idx_sales_branch_date`
      and Q-7 joins `sale_items` **once**, not per branch and not per page.
- [x] 3.10 Write `DT-14` into `docs/deuda_tecnica.md` — summary row in §2, detail card in §3 after `DT-13`, in
      Spanish, severity Media, status Aceptada, origin "contrato del módulo `analytics`", with contract §9.2's
      pay-off plan verbatim. **No `backend/init-db/` change accompanies it.**
- [x] 3.11 Update `openspec/PLAN.md` §1–§2 (**10/10 module packages, 39/39 use cases**, nothing left);
      confirm `/v3/api-docs` documents all seven operations (RNF-API-01); run
      `python3 scripts/validar_trazabilidad.py` (**14 DT declared, 14 with fiche**; RF/RNF/RN/CU unchanged at
      43 · 34 · 17 · 39), `./scripts/validar_esquema.sh` (green, unchanged) and `./mvnw verify` with
      `ModuleBoundariesTest` included.
