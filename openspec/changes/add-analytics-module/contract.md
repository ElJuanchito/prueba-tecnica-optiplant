# Contract — `add-analytics-module`

Acceptance contract for the `analytics` module package — the tenth and last of §2.4.
Step 1 of 3: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §3.7,
§3.8, §4 (RN-01 … RN-17) and §5.1–5.2, `docs/casos_de_uso.md` §2.3, §3.7, §3.9, and
`docs/historias_de_usuario.md` HU-DSH-01 … HU-DSH-03 alongside this document.

---

## 1. Scope

One module package, **`analytics`**: the read side of the system. It answers four questions and
writes nothing — the branch operational dashboard (sales trend, rotation / ABC, active-transfer
impact), the critical replenishment panel, the corporate comparative board across branches, and the
consolidated network availability an external ERP/POS queries by API key. Every figure it returns is
an aggregation over rows other modules already wrote.

**`analytics` is read-only, and that is an architectural commitment, not a description.** It declares
no `@Entity`, consumes no write port, opens no read-write transaction, and publishes no domain
event. `docs/decisiones_arquitectura_tecnica.md` §2.4 already states it "never mutates state"; this
contract makes that verifiable (§8, §10).

**Out of scope:** any schema change (zero `backend/init-db/` edits, §2.5 — including new indexes,
see §9.2); an `@Entity` over a table another module owns (§2.3); recomputing stock, cost, price or
transfer state — analytics reports, it does not derive business rules; historical stock snapshots or
a materialised rollup table; a caching layer (no Redis in the stack, RNF-DIS-01); alert generation
(`notifications` owns it); export to CSV/PDF; `CU-EXT-02`, already delivered inside `sales`; and the
frontend, which the project author builds separately.

---

## 2. Affected modules

### 2.1. Dependency directions

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `analytics` | `shared` | `AuthenticatedPrincipal`, `Role`, `PrincipalAccessor`, the network-availability read port (§2.4) | one-way |
| `analytics` → `inventory` | — | the `shared` read port only | no direct edge |
| `analytics` → `sales` / `transfers` / `catalog` / `iam` | — | **their tables**, read with native SQL (§2.3) | no compile-time edge |
| `inventory` | `shared` | implements the §2.4 port | one-way, already the direction of `StockMutationPort` |

`ModuleBoundariesTest.MODULOS` already lists `analytics` (`ModuleBoundariesTest.java:35-38`) — **no
ArchUnit change is needed**. The graph stays acyclic and `shared` still imports no module name.

### 2.2. Inherited decisions — not reopened

- **DT-10** Page size uses the `inventory`/`transfers` rejection pattern (default 20, max 100, `400` above it), never `catalog`'s silent clamp.
- **F-6 of `add-sales-module`** API keys are configuration-supplied and map to a service user already in `users`; compared in constant time, never logged.
- Roles are `ADMIN`, `BRANCH_MANAGER`, `OPERATOR` with **no `ROLE_` prefix**, enforced with `hasAuthority()`.

### 2.3. Decision — `analytics` reads foreign tables, it does not map them

**P-01** `analytics` MUST NOT declare a JPA `@Entity` for `sales`, `sale_items`, `branch_inventories`,
`kardex_movements`, `transfers`, `transfer_items`, `products`, `branches` or `users`. Two `@Entity`
classes over one table means two owners for one table — a boundary that exists in the package graph
and not in the database, which is worse than no boundary at all. Reads go through **native SQL with
projection interfaces or DTO projections**, exactly as `inventory` already resolves
`external_id → id` over `catalog`'s tables. The module owns no table and its adapters issue no
`INSERT`, `UPDATE` or `DELETE`.

**P-02** `analytics` MUST NOT consume `shared/stock/StockMutationPort`, `AuditWritePort`, or any
other outbound write port, and MUST NOT publish a domain event.

### 2.4. Decision — network availability crosses as a `shared` read port

**P-03** `CU-EXT-01` is a **second primary adapter** over the query `CU-INV-04` already answers
(PLAN.md §2), so it MUST NOT restate any of it. The controller lives in `analytics`, and the answer
comes from a new synchronous **read** port on `shared` — the `RouteLeadTimePort` / price-resolution
precedent — implemented by `inventory` over its existing use case. Reason: `availableStock` is a
derived semantic that `inventory` already defines (`BranchAvailability`), and recomputing it from
`branch_inventories` inside `analytics` would let two endpoints disagree about what "available"
means. The port takes a product `external_id` and no actor: `RF-EXT-01` is network-wide, so the
`isOwnBranch` marker is absent, as it already is for a corporate `ADMIN`.

**P-04** Nothing crosses the other way. No module reads or calls `analytics`; it is a leaf consumer.

### 2.5. Schema findings — reported, not migrated

| # | Finding | Resolution inside the existing schema |
| :--- | :--- | :--- |
| **F-1** | `sale_items` has **no `branch_id` and no date column** (`01-init-schema.sql:346-359`). | Every branch- and period-scoped item aggregation joins `sales` on `sale_id` via `idx_sale_items_sale`; the period filter lives on `sales.created_at`. Performance consequence in §9.2. |
| **F-2** | `sales.status` admits `CANCELLED` (`:332`). | **Every** sales aggregation MUST filter `status = 'COMPLETED'`. A voided sale that still counts inflates every dashboard figure and contradicts CU-VEN-03. |
| **F-3** | `idx_branch_inventory_critical(branch_id, current_stock, min_stock_threshold)` (`:207`) cannot serve `current_stock <= min_stock_threshold` — a column-to-column comparison is not a range predicate. | Only the `branch_id` prefix is used, filtering ~10 000 rows per branch (§5.1: 100 000 records / 10 branches). Measured acceptable against RNF-PER-01; **no index is added**. |
| **F-4** | `kardex_movements` has **no `(branch_id, created_at)` composite**: `idx_kardex_branch_product` does not lead with a date and `idx_kardex_created_at` is not branch-scoped (`:240-241`). At 5 000 000 rows (§5.1) any period-scoped branch scan is unbounded. | `analytics` MUST NOT read `kardex_movements` for any indicator in this cycle. Rotation and demand come from `sales`/`sale_items`, which is also the correct source: the Kardex mixes transfers and adjustments, which are movement, not demand. |
| **F-5** | No table stores a **historical stock snapshot**, so true inventory turnover (COGS ÷ average inventory) is not computable. | Reported as `unitsSold`, `salesAmount`, `abcClass` and `coverageDays` = `current_stock ÷ (unitsSold ÷ periodDays)` — an honest coverage proxy, never a turnover ratio the data cannot support (PA-03). |
| **F-6** | There is **no API-key table**, and `sales`' `ExternalApiKeyProperties` lives in `sales.infrastructure.config` — importing it would create the forbidden `analytics → sales` edge. | `analytics` declares its own configuration-properties record, filter and `SecurityFilterChain` under `analytics/infrastructure/config`, prefix `optiplant.analytics.external`, each key mapping to one service-user `external_id`. ~80 duplicated mechanical lines, zero risk to the archived `sales` path (PA-05). |
| **F-7** | `transfers` denormalises no quantity; the stock impact needs `transfer_items`. | Joined directly — §5.1 caps active transfers at 200, so the join is trivial. |
| **F-8** | `branches.is_active` and `products.is_active` exist (`:23`, `:102`). | The corporate board lists **active** branches only; an inactive branch with history is excluded, not silently zeroed. Replenishment lists active products only. |

None of these blocks any of the four use cases. **No `backend/init-db/` change is proposed.**

---

## 3. Traceability

Every identifier below was verified present in its source document.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| RF-DSH-01, RF-DSH-02, RF-DSH-03 | CU-DSH-01 | HU-DSH-01 |
| RF-DSH-04 | CU-DSH-02 | HU-DSH-02 |
| RF-DSH-05 | CU-DSH-03 | HU-DSH-03 |
| RF-EXT-01 | CU-EXT-01 | — *(no HU exists; `Could` priority, SRS §3.9)* |
| RF-INV-04 | CU-INV-04 → CU-EXT-01 *(the availability query the adapter re-exposes)* | HU-INV-01 |
| RF-INV-07 | CU-DSH-02 *(the threshold RF-DSH-04 renders)* | HU-DSH-02 |
| RF-LOG-01 | CU-LOG-02 → CU-DSH-01 *(RF-DSH-03 is traced to both, matrix line 602)* | HU-LOG-01 |
| RN-08, RN-09, RN-12, RN-13, RN-14 | constrain the above | — |

**No new `RF` / `RNF` / `RN` is required**, so `docs/` needs no edit and the traceability matrix of
`docs/casos_de_uso.md` gains no row. `python3 scripts/validar_trazabilidad.py` — verified green while
writing this contract (43 RF · 34 RNF · 17 RN · 39 CU · 13 DT) — must still pass unchanged. §9.2's
debt item adds `DT-14` to `docs/deuda_tecnica.md`, which the validator checks has a detail card.

---

## 4. Behavioural contract

**R-00** Every collection endpoint MUST paginate (default size 20, max 100) and MUST **reject** an
out-of-range page or size with `400 invalid_request` (`DT-10`, RNF-PER-04). No endpoint accepts the
acting branch from a non-`ADMIN` caller (RN-14, R-02).

**R-01** Every operation in this module MUST be a read. *Given* any request to any `analytics`
endpoint, *then* no row is inserted, updated or deleted in any table, and no domain event is
published (P-01, P-02).

**R-02** The branch scope MUST come from `AuthenticatedPrincipal.branchId` (RN-14). *Given* a
`BRANCH_MANAGER` or `OPERATOR` sending `branchExternalId`, *then* `403 cross_branch_access_denied` —
refused, never silently ignored, because a silently dropped parameter hides a client bug.
*Given* an `ADMIN` sending it, *then* it is honoured (RN-08 and §2.3 both grant `ADMIN` cross-branch
**reads**; RN-14 governs the branch an operation *acts on*). *Given* a corporate `ADMIN`
(`branchId == null`) omitting it on a branch dashboard, *then* `403 branch_context_required` naming
`/api/analytics/corporate/branches` as the cross-branch view — the network-wide rollup exists in
exactly one place (RNF-PER-03). *Given* an unknown or inactive `branchExternalId`, *then*
`404 branch_not_found`.

**R-03** Every sales figure MUST count only `status = 'COMPLETED'` sales (F-2). *Given* a sale voided
after being counted, *when* the dashboard is queried again, *then* the figure has decreased.

### Sales trend (CU-DSH-01, RF-DSH-01)

- **R-04** The response MUST carry one entry per calendar month for the requested window (`months`, default 4 — the current month plus the three prior HU-DSH-01 names), each with `salesCount`, `unitsSold` and `totalAmount`, ordered oldest first.
- **R-05** It MUST include `monthOverMonthVariationPercent`, the current month against the immediately previous one (HU-DSH-01). *Given* a previous month with zero sales, *then* the variation is `null`, never a division by zero and never `100`.
- **R-06** *Given* a branch with no sales in the window, *then* `200` with every month present at zero and an `empty` marker — an informative empty state, never `404` and never an error (HU-DSH-01).
- **R-07** *Given* `months` outside `1 … 12`, *then* `400 invalid_request`: an unbounded window is an unbounded scan (RNF-PER-04 in spirit).

### Rotation and ABC / Pareto (CU-DSH-01, RF-DSH-02)

- **R-08** For a period (`from`/`to`, defaulting to the current calendar month) the response MUST rank products by `salesAmount` and return, per product, `sku`, `name`, `unitsSold`, `salesAmount`, `sharePercent`, `cumulativeSharePercent`, `abcClass` and `coverageDays` (F-5).
- **R-09** `abcClass` MUST be `A` while cumulative share `<= 80 %`, `B` while `<= 95 %`, `C` beyond (PA-02). The classification MUST be computed over the **whole** ranked period, not over the requested page: an ABC class that changes with pagination is not a classification.
- **R-10** `direction=TOP` MUST return highest demand first and `direction=BOTTOM` lowest first, which is HU-DSH-01's "mayor y menor rotación". *Given* a product with sales in the period and zero current stock, *then* `coverageDays` is `0`; *given* zero `unitsSold`, *then* `coverageDays` is `null`, never infinity.
- **R-11** *Given* `from` after `to`, or a window wider than 366 days, *then* `400 invalid_request`.

### Active transfers and their stock impact (CU-DSH-01, RF-DSH-03)

- **R-12** The summary MUST report, for the scoped branch, the count of transfers by status among `REQUESTED`, `IN_PREPARATION`, `IN_TRANSIT`, split into inbound (destination) and outbound (origin), plus the count whose `estimated_arrival_at` is past with no `actual_arrival_at` (RF-LOG-01).
- **R-13** The impact list MUST report, per product touched by an active transfer, `currentStock`, `inboundInTransit` (Σ `dispatched_quantity` of `IN_TRANSIT` transfers arriving), `outboundCommitted` (Σ `requested_quantity` of `REQUESTED`/`IN_PREPARATION` transfers leaving) and `projectedStock = currentStock + inboundInTransit − outboundCommitted`.
- **R-14** `analytics` MUST report `branch_inventories.in_transit_stock` as `inventory` maintains it and MUST NOT recompute or correct it. A discrepancy between it and `inboundInTransit` is `inventory`'s defect to fix, not a value for `analytics` to invent.

### Critical replenishment (CU-DSH-02, RF-DSH-04)

- **R-15** The panel MUST list active products of the scoped branch whose `current_stock <= min_stock_threshold`, each with `currentStock`, `minStockThreshold`, `severity` and `coverageDays`.
- **R-16** `severity` MUST be `OUT_OF_STOCK` when `current_stock = 0` and `CRITICAL` otherwise, and the default sort MUST place `OUT_OF_STOCK` first (HU-DSH-02: critical severity and higher order priority).
- **R-17** The response MUST carry `productExternalId` so the client can invoke the existing purchase-order and transfer-request endpoints (HU-DSH-02's direct actions). `analytics` MUST NOT create either — that would make it a writer (R-01).
- **R-18** *Given* a branch with nothing below threshold, *then* `200` with an empty page, never `404`.

### Corporate comparative board (CU-DSH-03, RF-DSH-05)

- **R-19** Restricted to `ADMIN` (§5). *Given* a `BRANCH_MANAGER` or `OPERATOR`, *then* `403 forbidden` (HU-DSH-03, third criterion).
- **R-20** One row per **active** branch (F-8) with `salesAmount`, `salesCount`, `unitsSold`, `inventoryValue` (Σ `current_stock × average_cost`), `criticalProductCount` and `activeTransferCount`, for the requested month (default: the current one).
- **R-21** Every indicator in R-20 MUST be sortable ascending and descending (HU-DSH-03, second criterion). *Given* an unknown sort key, *then* `400 invalid_request` — never a silent fallback to the default order, which would show the user a ranking they did not ask for.
- **R-22** `inventoryValue` MUST use the branch's own `average_cost` (RN-03's weighted average, as `purchases` maintains it), and MUST NOT be derived from sale prices.

### Consolidated network availability (CU-EXT-01, RF-EXT-01)

- **R-23** The endpoint MUST return, for one product `external_id`, the same payload `CU-INV-04` returns — per active branch `currentStock`, `reservedStock`, `inTransitStock`, `availableStock`, plus `networkTotal` — obtained through the P-03 port, with **zero** logic restated.
- **R-24** The `isOwnBranch` marker MUST be absent: an external system has no branch (P-03). *Given* a product with zero stock everywhere, *then* an explicit zeroed result, never `404`. *Given* an unknown product, *then* `404 product_not_found`.
- **R-25** *Given* an absent, malformed or unknown API key, *then* `401 invalid_api_credential`, with no hint as to which part failed and no key material in any log (F-6).
- **R-26** The response MUST expose `external_id` only, and MUST NOT expose cost, `average_cost`, price or any sales figure: an availability contract shows quantities (RNF-API-02, §7).

---

## 5. Authorization matrix

Cross-checked against `docs/casos_de_uso.md` §2.3 — "Dashboard de sucursal propia" is granted to all
three roles, "Dashboard corporativo comparativo" to `ADMIN` only, and network-wide stock reads to the
external system. Enforced with `hasAuthority()`, never `hasRole()`.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | External | Branch rule |
| :--- | :---: | :---: | :---: | :---: | :--- |
| Sales trend (CU-DSH-01) | ✅\* | ✅ | ✅ | ❌ | session branch; `ADMIN` MUST name `branchExternalId` |
| Rotation / ABC (CU-DSH-01) | ✅\* | ✅ | ✅ | ❌ | idem |
| Active-transfer summary and impact (CU-DSH-01) | ✅\* | ✅ | ✅ | ❌ | idem |
| Replenishment panel (CU-DSH-02) | ✅\* | ✅ | ✅ | ❌ | idem |
| Corporate board (CU-DSH-03) | ✅ | ❌ | ❌ | ❌ | all active branches; `branchExternalId` is **not** accepted |
| Network availability (CU-EXT-01) | ❌ | ❌ | ❌ | ✅ | none — network-wide by definition |

**\*** A corporate `ADMIN` has `branchId == null`. Unlike a mutation, that is **not** an error here —
it is precisely the actor RF-DSH-05 exists for. On the four branch dashboards the `ADMIN` names the
branch with `branchExternalId` (R-02); omitting it answers `403 branch_context_required` pointing at
the corporate board, so the expensive cross-branch rollup lives in one endpoint (PA-01, RNF-PER-03).
A branch-assigned `ADMIN` may still omit it and gets their own branch.

---

## 6. API surface

All identifiers are `external_id` UUIDs (RNF-API-02); no numeric `id` appears in any field, message
or header. Page envelope matches the existing controllers: `{ content, totalElements, page, size }`;
errors use `{ code, message }`. Quantities are decimals in the base unit (RN-13); money is decimal.
`branchExternalId` is `ADMIN`-only wherever it appears (R-02).

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/analytics/dashboard/sales-trend` | RF-DSH-01 | `months?` (1–12, default 4), `branchExternalId?` | `{ branchExternalId, months: [{ year, month, salesCount, unitsSold, totalAmount }], monthOverMonthVariationPercent, empty }` |
| `GET` | `/api/analytics/dashboard/rotation` | RF-DSH-02 | `from?`, `to?`, `direction?` (`TOP`\|`BOTTOM`), `branchExternalId?`, `page`, `size` | page of `{ productExternalId, sku, name, unitsSold, salesAmount, sharePercent, cumulativeSharePercent, abcClass, coverageDays }` |
| `GET` | `/api/analytics/dashboard/transfers` | RF-DSH-03 | `branchExternalId?` | `{ inbound: { requested, inPreparation, inTransit }, outbound: { … }, delayedCount }` |
| `GET` | `/api/analytics/dashboard/transfers/stock-impact` | RF-DSH-03 | `branchExternalId?`, `page`, `size` | page of `{ productExternalId, sku, name, currentStock, inTransitStock, inboundInTransit, outboundCommitted, projectedStock }` |
| `GET` | `/api/analytics/replenishment` | RF-DSH-04 | `severity?` (`OUT_OF_STOCK`\|`CRITICAL`), `sort?` (`severity`\|`product`\|`coverage`), `branchExternalId?`, `page`, `size` | page of `{ productExternalId, sku, name, currentStock, minStockThreshold, severity, coverageDays }` |
| `GET` | `/api/analytics/corporate/branches` | RF-DSH-05 | `year?`, `month?`, `sort?` (any R-20 indicator), `direction?`, `page`, `size` | page of `{ branchExternalId, code, name, salesAmount, salesCount, unitsSold, inventoryValue, criticalProductCount, activeTransferCount }` |
| `GET` | `/api/external/availability/{productExternalId}` | CU-EXT-01 | header `X-Api-Key` | `{ productExternalId, sku, name, branches: [{ branchExternalId, branchName, currentStock, reservedStock, inTransitStock, availableStock }], networkTotal }` |

**No composite "whole dashboard" endpoint is offered** (PA-04): one indicator per request means a
slow aggregation degrades one card rather than the whole page, each response is independently
measurable against RNF-PER-01, and the client parallelises. `/api/analytics/**` is bearer-authenticated
under IAM's chain; `/api/external/availability/**` gets its own `@Order`-ed chain (F-6), placed before
IAM's catch-all and matched exactly, so it never shadows `/api/external/sales`.

---

## 7. Error taxonomy

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` | 400 | R-00 page or size out of range; `months` outside 1–12 (R-07); inverted or over-wide date range (R-11); unknown `sort` or `direction` (R-21); malformed UUID |
| `invalid_api_credential` | 401 | R-25 — absent, malformed or unknown API key |
| `forbidden` | 403 | R-19 — a non-`ADMIN` reaching the corporate board (Spring Security's decision, uniform envelope) |
| `branch_context_required` | 403 | R-02 — corporate `ADMIN` on a branch dashboard with no `branchExternalId` |
| `cross_branch_access_denied` | 403 | R-02 — a `BRANCH_MANAGER` or `OPERATOR` sending `branchExternalId` |
| `branch_not_found` | 404 | R-02 — `ADMIN` names an `external_id` that is no active branch |
| `product_not_found` | 404 | R-24 — CU-EXT-01 with an unknown product |

Codes reuse the strings the existing handlers already emit (`InventoryExceptionHandler`), so a client
learns one vocabulary.

**Must not leak.** Whether a branch exists that the caller may not read — a non-`ADMIN` gets
`cross_branch_access_denied` before any lookup, so no probe distinguishes a real branch from an
invented one; another branch's figures in any branch-scoped response; `average_cost`, unit cost or
any valuation on the CU-EXT-01 payload (R-26) or on any endpoint an `OPERATOR` can reach —
`inventoryValue` is corporate-board-only; numeric `id` values anywhere; API-key material in logs,
messages or traces; which half of a credential was wrong (R-25); stack traces, SQL text, index or
constraint names, or JPA exception messages.

---

## 8. Transactional and consistency guarantees

- **T-01** Every `analytics` service method MUST be annotated `@Transactional(readOnly = true)`. There is no read-write transaction anywhere in the module, and a reviewer can grep for it (§10).
- **T-02** No locking of any kind — no `FOR UPDATE`, no `LockModeType`, no advisory lock. RN-09 and RNF-PER-03: an analytics read MUST NOT slow or block a branch's transactional work.
- **T-03** Nothing is atomic with anything: each endpoint is one statement or one small set of statements over committed data. Figures are consistent as of the moment of the read, and the contract makes no claim beyond that — a dashboard is a snapshot, not a ledger.
- **T-04** No `AFTER_COMMIT` listener, because there is no commit to hang one on.
- **T-05** Every read is idempotent and safe: `GET` only, and repeating a request changes nothing. All seven endpoints are cacheable by the client; the server adds no cache (out of scope, §1).
- **T-06** The CU-EXT-01 port call (P-03) is a plain synchronous read inside the same read-only transaction — never `@Async`, never an event.

---

## 9. Non-functional obligations

### 9.1. General

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-04 | every collection paginated, out-of-range rejected | `400 invalid_request` (R-00, `DT-10`) |
| RNF-PER-03 | no analytics read degrades transactional work | T-02: no locks, `readOnly = true` (RN-09) |
| RNF-SEC-01 | role checks with `hasAuthority()`, no `ROLE_` prefix | §5 matchers plus method-level checks |
| RNF-SEC-03 | branch isolation on every read | R-02, proven by `AnalyticsBranchIsolationIT` |
| RNF-SEC-05 | all input validated in the backend | bean validation on every query parameter before any query runs |
| RNF-API-01 | OpenAPI documents each endpoint, its statuses and its error envelope | `/v3/api-docs` contains all seven operations |
| RNF-API-02 | only `external_id` on the wire | §6, asserted on response shape in the smoke tests |
| RNF-OBS-01 | structured logs carry correlation id, user, branch, operation | API-key material never logged (R-25) |
| RNF-MAN-01 | ABC classification and severity rules covered by automated tests | §10 |
| RNF-MAN-02 | module boundaries verified | `ModuleBoundariesTest` green with `analytics` present |

### 9.2. RNF-PER-01 — p95 < 200 ms, and where it is at risk

Assessed against the §5.1 reference load: 10 branches, 10 000 products, 100 000 inventory rows,
5 000 sales/day network-wide (≈ 500/branch/day, ≈ 150 000/month network-wide), 5 000 000 Kardex rows
at two years, 200 active transfers. Each aggregation is stated with the access path it must use.

| # | Aggregation | Rows touched | Existing index | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| A-1 | Replenishment panel (R-15) | ~10 000 per branch | `idx_branch_inventory_critical` prefix only (F-3) | **Sufficient.** A 10 000-row filtered scan is well inside budget; no index added. |
| A-2 | Active transfers, summary and impact (R-12, R-13) | ≤ 200 transfers, ≤ ~1 000 items | `idx_transfers_origin`, `idx_transfers_destination`, `idx_transfers_status`, `idx_transfer_items_transfer` | **Sufficient.** |
| A-3 | Corporate inventory value (R-20, R-22) | 100 000 rows, `SUM … GROUP BY branch_id` | full scan of a narrow table | **Sufficient.** A sequential aggregate over 100 000 narrow rows is tens of milliseconds. |
| A-4 | Sales trend, one branch, 4 months (R-04) | ~60 000 `sales` rows | `idx_sales_branch_date(branch_id, created_at)` — range scan, but `total_amount` is not in the index, so 60 000 heap fetches | **At risk.** Must be measured; if p95 exceeds 200 ms the fix is a covering index, which is a schema change — deferred to `DT-14`, not taken here. |
| A-5 | Rotation / ABC, one branch, one month (R-08) | ~15 000 `sales` ⋈ ~45 000 `sale_items` | `idx_sales_branch_date` then `idx_sale_items_sale` per sale (F-1) | **At risk**, same treatment as A-4. |
| A-6 | Corporate sales and units, all branches, one month (R-20) | ~150 000 `sales` ⋈ ~450 000 `sale_items`, grouped by branch | `idx_sales_branch_date` + 150 000 nested `idx_sale_items_sale` lookups (F-1) | **Exceeds budget.** This is the one aggregation the current schema cannot serve in 200 ms. |
| A-7 | Anything over `kardex_movements` | up to 5 000 000 | none usable (F-4) | **Forbidden this cycle.** `analytics` issues no query against the Kardex (F-4). |

**`DT-14` — the corporate month rollup joins `sale_items` with no covering path.** To be written into
`docs/deuda_tecnica.md` with its detail card in the verification slice (§10), severity Media, status
Aceptada. Mitigation now, without touching the schema: A-6 is `ADMIN`-only and low-frequency (§5.1
puts 50 concurrent users at peak, of which the corporate board is a handful), it aggregates one
month, and `sale_items` is joined **once** per response — never once per branch and never per page,
which would turn a slow query into ten. Pay-off plan, at the next schema change (`DT-01`'s Flyway
migration is the natural carrier): `CREATE INDEX ON sales(branch_id, created_at) INCLUDE
(total_amount)` and `CREATE INDEX ON sale_items(sale_id) INCLUDE (product_id, quantity, subtotal)`,
turning A-4, A-5 and A-6 into index-only scans; if that is still short, a nightly rollup table. **No
index is created in this cycle** — the contract records the debt, the schema stays untouched.

---

## 10. Definition of done

Verifiable in the three planned PRs (`openspec/PLAN.md` §3).

**PR 1 — domain + application**

- [ ] `com.optiplant.inventory.analytics.domain` and `…analytics.application` exist with no Spring or Jakarta import in `domain`; the `shared` network-availability read port (P-03) exists and `shared` still imports no module name.
- [ ] No class is added to a direct sub-package of `com.optiplant.inventory` other than `analytics`.
- [ ] Unit `*Test` (no Docker) covering R-09 ABC boundaries (exactly 80 %, exactly 95 %, a single product taking 100 %), R-05 variation including the zero-previous-month case, R-10 and R-15's `coverageDays` edge cases (zero stock, zero demand), R-16 severity ordering, and R-00 page-size rejection.
- [ ] `cd backend && ./mvnw verify` green.

**PR 2 — infrastructure + web**

- [ ] Native-SQL read adapters with projections; `rg "@Entity" backend/src/main/java/com/optiplant/inventory/analytics` returns **nothing** (P-01).
- [ ] `rg "@Transactional" …/analytics` shows `readOnly = true` on every occurrence, and `rg "INSERT|UPDATE|DELETE|StockMutationPort|AuditWritePort|ApplicationEventPublisher" …/analytics` returns nothing (R-01, P-02, T-01).
- [ ] Every sales aggregation filters `status = 'COMPLETED'` (F-2) and none queries `kardex_movements` (F-4) — both greppable in the adapter SQL.
- [ ] Controllers, exception handler and `SecurityConfig` matchers for `/api/analytics/**` using `hasAuthority()` (§5), plus the `analytics` API-key chain for `/api/external/availability/**` (F-6) declared with an `@Order` that does not shadow `/api/external/sales`.
- [ ] `inventory` implements the P-03 port; `analytics` consumes it and implements nothing.
- [ ] Every §7 code is reachable from at least one controller path — no dead error code.
- [ ] `./scripts/validar_esquema.sh` green — expected **unaffected**, since no `backend/init-db/` file changes (§2.5). If it must change, §2.5 was wrong: stop and report.
- [ ] `cd backend && ./mvnw verify` green.

**PR 3 — verification** — Testcontainers `*IT` reserved for what can break the system.

- [ ] `AnalyticsReadOnlyIT` — R-01: after exercising all seven endpoints, row counts of `sales`, `sale_items`, `branch_inventories`, `kardex_movements`, `transfers` and `audit_logs` are unchanged, and no `system_alerts` row appeared.
- [ ] `AnalyticsBranchIsolationIT` — R-02/§5: branch A's dashboards never show branch B's figures; a `BRANCH_MANAGER` sending `branchExternalId` gets `403 cross_branch_access_denied`; a corporate `ADMIN` gets `403 branch_context_required` without it and real data with it; a `BRANCH_MANAGER` gets `403` on the corporate board.
- [ ] `SalesTrendAndRotationIT` — R-03/R-04/R-08/R-09: figures computed against real PostgreSQL over seeded sales; a voided sale drops out of every figure; ABC classes are stable across pages.
- [ ] `ReplenishmentPanelIT` — R-15/R-16: the column-to-column threshold predicate returns the right rows against real PostgreSQL (F-3), out-of-stock first, empty branch answers an empty page.
- [ ] `ExternalAvailabilityIT` — R-23/R-24/R-25: the API-key path returns exactly the CU-INV-04 payload, absent/unknown keys get `401 invalid_api_credential`, an unknown product `404`, a zero-stock product an explicit zeroed result.
- [ ] Smoke coverage of the remaining read endpoints (status, envelope shape, no numeric `id`, no cost field where §7 forbids it).
- [ ] `DT-14` written into `docs/deuda_tecnica.md` (summary row **and** detail card, in Spanish) with the §9.2 pay-off plan; no `backend/init-db/` change accompanies it.
- [ ] `python3 scripts/validar_trazabilidad.py` green — 14 DT declared, 14 with a card.
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

---

## 11. Open questions

None blocking. Six decisions were taken here rather than escalated, each with its reversal cost.

- **PA-01 — A corporate `ADMIN` names `branchExternalId` on branch dashboards; omitting it is `403 branch_context_required` (R-02).** RN-08 and §2.3 grant `ADMIN` cross-branch reads, so accepting the parameter for that role does not weaken RN-14, which governs the branch an operation *acts on*. Answering a network-wide aggregate instead would put the A-6 rollup behind five endpoints rather than one (RNF-PER-03, §9.2). Reversal: one branch in the scope resolver.
- **PA-02 — ABC thresholds are 80 % / 95 % of cumulative sales amount, classified over the whole period (R-09).** No `RN` defines them; these are the standard Pareto cut-points, and RF-DSH-02 names "análisis ABC / Pareto" without parameters. Ranking by amount rather than units keeps a cheap high-volume product out of class A. Reversal: two constants, or configuration properties if a user ever asks.
- **PA-03 — Rotation is reported as `unitsSold` plus `coverageDays`, not as a turnover ratio (F-5).** No historical stock snapshot exists, so average inventory over a period is not computable; publishing a ratio derived from *current* stock would be a number that looks precise and is not. Reversal: add the ratio once a snapshot or rollup table exists (`DT-14`'s pay-off is the natural carrier).
- **PA-04 — One endpoint per indicator, no composite dashboard endpoint (§6).** RNF-PER-01 is measured per request: a composite response is as slow as its slowest aggregation and hides which one failed. Reversal: a façade controller composing the existing services, added later without changing any of them.
- **PA-05 — `analytics` duplicates the API-key filter rather than importing `sales`' or promoting it to `shared` (F-6).** Importing creates the `analytics → sales` edge ArchUnit forbids; promoting it to `shared` would force a refactor of an archived, working module for ~80 mechanical lines. Reversal: move both to `shared/security` in one refactor when a third API-key consumer appears.
- **PA-06 — Rotation and demand come from `sales`/`sale_items`, never from `kardex_movements` (F-4, A-7).** The Kardex has no `(branch_id, created_at)` index and reaches 5 000 000 rows, and it mixes transfers and adjustments, which are movement, not demand. Reversal: only with the index `DT-14` proposes, and only if an indicator ever needs movement rather than demand.
