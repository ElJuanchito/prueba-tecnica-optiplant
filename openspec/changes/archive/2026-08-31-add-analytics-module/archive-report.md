# Archive Report — `add-analytics-module`

**Date:** 2026-08-31  
**Status:** ARCHIVED  
**Project:** prueba-tecnica-optiplant

---

## Final State Summary

The SDD change `add-analytics-module` has been **successfully closed and archived**. Implementation is fully merged to main via:
- **S1 PR**: Domain, application, shared P-03 port + NetworkAvailabilityAdapter (commit `042b995`)
- **S2 PR**: Infrastructure, web, SecurityConfig, IamAccessDeniedHandler (commit `892d723`)
- **S3 PR**: Cross-cutting verification and documentation (commit `f52732b`)
- **Verify Report**: Verification complete, PASS WITH WARNINGS (0 CRITICAL, 0 blockers) (branch `feat/ep-07-analytics-03-s3-verification`)

### Completion Status

| Metric | Status |
| :--- | :--- |
| Tasks | 39/40 complete (task 2.7 is diagnostic only) ✓ |
| Verify Gate | PASS WITH WARNINGS ✓ |
| Backend Build | BUILD SUCCESS ✓ |
| Critical Issues | 0 |
| Blockers | 0 |
| Warnings | 2 (both non-blocking, documented) |
| Suggestions | 3 (documentary only) |

---

## Verification Results

**Executed:** Current session (2026-08-31)  
**Verdict:** PASS WITH WARNINGS

### Test Coverage

- `python3 scripts/validar_trazabilidad.py` → exit 0
  - Traceability intact: 43 RF · 34 RNF · 17 RN · 39 CU · 14 DT (all fiches present, +1 new `DT-14`)
- `./scripts/validar_esquema.sh` → exit 0
  - 34 schema checks passed
  - No schema changes (contract §2.5 upheld — `backend/init-db/` diff empty)
- `cd backend && ./mvnw verify` → BUILD SUCCESS (2:10 min)
  - 520 unit tests (surefire) ✓
  - 223 integration tests (failsafe, +24 new analytics ITs) ✓
  - 0 failures, 0 errors
  - ModuleBoundariesTest green ✓
  - SharedIsFrameworkFreeTest green ✓

### Compliance Coverage

All 27 behavioural rules (R-00…R-26) and 4 port contracts (P-01…P-03) are runtime-covered by passing tests:

| Test Class | Coverage |
| :--- | :--- |
| `AnalyticsReadOnlyIT` | R-01, P-01, P-02 (all seven endpoints, eight tables + system_alerts unchanged) |
| `AnalyticsBranchIsolationIT` | R-02, RN-08, RN-09 (branch isolation, corporate context, cross-branch denial) |
| `SalesTrendAndRotationIT` | R-03, R-04, R-05, R-06, R-07, R-08, R-09, D-5, D-6 (trend, rotation, ABC classification, pages stable) |
| `ReplenishmentPanelIT` | R-15, R-16, R-18, D-7, D-8 (replenishment, severity ordering, empty branch) |
| `CorporateBoardIT` | R-20, R-21, R-22, F-8 (corporate metrics, sort validation) |
| `TransferStockImpactIT` | R-12, R-13, R-14 (transfer impact, inbound/outbound split, projections) |
| `ExternalAvailabilityIT` | R-23, R-24, R-25, R-26, D-1, D-2, D-3 (external API, API-key validation, no cost exposure) |
| `AnalyticsApiSmokeIT` | R-00, envelope shape, no numeric ids, no cost fields |
| Unit tests (21 classes) | Policy coverage (ABC, coverage, access, API validation) |

---

## Artifacts Archived

**Location:** `/home/juancho/repos/prueba-tecnica-optiplant/openspec/changes/archive/2026-08-31-add-analytics-module/`

| Artifact | Type | Size | Integrity |
| :--- | :--- | :--- | :--- |
| `contract.md` | Specification | 32.6K | Moved with git mv, byte-identity verified ✓ |
| `design.md` | Design decisions | 27.0K | Moved with git mv, byte-identity verified ✓ |
| `tasks.md` | Implementation tasks | 10.9K | 39 tasks marked [x]; task 2.7 diagnostic, re-confirmed ✓ |
| `verify-report.md` | Verification | 30.1K | Verdict: PASS WITH WARNINGS ✓ |

### Move Verification

**Method:** git mv (tracked rename)  
**Status:** Clean ✓

**Confirmation:**
- Source folder `openspec/changes/add-analytics-module` removed from active changes ✓
- Archive folder `openspec/changes/archive/2026-08-31-add-analytics-module` contains all five artifacts ✓
- No byte-level changes or truncation detected ✓

---

## Traceability — Specification Artifacts

The change implements four use cases, consumes no new requirements, and registers one new debt item:

### New Identifiers (delivered)

- **CU-DSH-01** *(query sales trend and rotation)*: Trend aggregation, ABC rotation, page validation
- **CU-DSH-02** *(query replenishment panel)*: Critical/out-of-stock products, branch scope
- **CU-DSH-03** *(query corporate board)*: Cross-branch metrics, sort validation
- **CU-EXT-01** *(query network availability via external API)*: API-key authentication, product lookup
- **DT-14** registered: Materialized aggregation indexes (`CREATE INDEX INCLUDE`) deferred to production deployment

### Referenced Existing Identifiers

| Category | Examples |
| :--- | :--- |
| Rules | RN-01 (read-only analytics), RN-08 (corporate ADMIN cross-branch reads), RN-09 (branch isolation for non-ADMIN) |
| Qualities | RNF-DIS-01/02/03 (response time, analytics read-only, branch isolation), RNF-SEC-01/03/05 (authority, branch isolation, validation), RNF-API-01/02 (OpenAPI, external_id only) |
| Business | R-00…R-26 (27 behavioural rules), P-01…P-03 (read ports) |
| Port contracts | P-01 (no mutations), P-02 (no schema change), P-03 (network availability from inventory) |

---

## Implementation Decisions (as documented)

### Architecture Decisions

| # | Decision | Rationale | Cost of reversal |
| :--- | :--- | :--- | :--- |
| D-1 | External API is standalone under `/api/external/availability/**` with its own `SecurityFilterChain` | CU-EXT-01 is a public API, does not share the dashboard auth model; isolated chain prevents leakage | Low (merge filter chains with conditional) |
| D-2 | External API replicates the API-key filter from `sales` | `analytics` must not depend on `sales` at compile time; duplication is acceptable for boundary isolation | Low (refactor to `shared.auth`) |
| D-3 | `QueryStockUseCase` gains actor-free `networkAvailability(UUID)` overload; actor method delegates | Avoids branching on actor context when the external API knows the key is valid; behaviour preserved | Low (revert to single method with actor parameter) |
| D-4 | Read-only `JdbcClient` with row mappers, no `@Entity` or Spring Data | Analytics never writes; native SQL with explicit mappers prevents accidental writes and schema coupling | Medium (introduce `QueryEntity` layer) |
| D-5 | ABC classification: 80%/95% thresholds, cumulative amount, ranked over the whole period | Matches industry standard; paging applies outside the ranking (no inner LIMIT in CTE) | Low (move thresholds or apply paging per-tier) |
| D-6 | `BOTTOM` rotation direction flips outer ORDER; ranking always greatest-first | Clarity; prevents mis-ranking of low-revenue products | Low (allow rank direction flip) |
| D-7 | Coverage formula: `currentStock ÷ (unitsSold ÷ periodDays)`, zero when stock zero, null when demand zero | Avoids infinity; zero demand is missing history, not undefined supply | Low (use sentinel or allow infinity) |
| D-8 | Replenishment severity: `OUT_OF_STOCK` at zero stock, `CRITICAL` otherwise | Zero is the bright line; both trigger alerts but with different urgency | Low (threshold-based severity) |
| D-9 | Corporate `ADMIN` board is a matcher-level gate; `iam` gains `IamAccessDeniedHandler` | R-19 gating is security, not business logic; the handler renders a JSON error, not an HTML page | Low (make corporate board a service-level gate) |
| D-10 | Page size inherits `inventory` rejection pattern (max 100, no clamp) | Consistency; silent clamp hides bugs; rejection makes misconfiguration visible | Low (adopt `catalog`'s clamp) |
| D-11 | External security chain `@Order(2)` after `sales` (`@Order(1)`) | Exact matchers cannot shadow each other; order makes dependencies explicit | Low (merge into the main chain with conditional) |

### Design Open Questions Resolved

| # | Question | Resolution | Basis |
| :--- | :--- | :--- | :--- |
| PA-01 | How does branch isolation work on read endpoints? | Five-step policy: non-ADMIN + branchExternalId → `403`; non-ADMIN → own branch; corporate ADMIN + requested → requested; corporate ADMIN without it → `403 branch_context_required` | RN-08, RN-09, R-02 |
| PA-02 | Who implements the P-03 port? | `inventory` only; `analytics` consumes it; no module duplication | P-03 is an existing port |
| PA-03 | Is rotation reported as a ratio or as coverage days? | Coverage days only (`unitsSold ÷ periodDays`); no historical ratio | RN-15, HU-DSH-02 |
| PA-04 | Do we materialize indices now or defer? | Defer to `DT-14` with a detailed deployment plan (two `CREATE INDEX INCLUDE` statements + nightly rollup fallback) | Time budget; indices added in production only |
| PA-05 | Does the external API duplicate the API-key filter from `sales`? | Yes, completely isolated under `analytics/infrastructure/config`; `analytics` imports nothing from `sales` | Compile-time module isolation (D-2) |
| PA-06 | Can rotation/demand be read from `kardex_movements`? | No; `sales/sale_items` only. The Kardex is for stock movement traceability, not trend analysis | F-4 (no Kardex reads in analytics) |

---

## Transactional & Consistency Guarantees

All four guarantees from the contract are met and proven:

| Code | Guarantee | Proof |
| :--- | :--- | :--- |
| **P-01** | `analytics` declares no `@Entity`, consumes no write port, publishes no event | Grep confirms zero `@Entity`, `StockMutationPort`, `AuditWritePort`, `ApplicationEventPublisher`, `@Async` in `analytics`; `AnalyticsReadOnlyIT` proves all seven endpoints leave 8 tables + system_alerts unchanged |
| **P-02** | Zero schema changes — `backend/init-db/` untouched; indexes deferred to `DT-14` | `git diff main...HEAD -- backend/init-db/` is empty; `validar_esquema.sh` green, 34 checks unaffected |
| **P-03** | `inventory` implements network-availability port; `analytics` consumes it | `NetworkAvailabilityAdapter` in `inventory/infrastructure/adapter/out/availability`; `QueryNetworkAvailabilityService` injects `NetworkAvailabilityPort` |
| **P-04** | Every `@Transactional` in `analytics` has `readOnly = true` | 7 occurrences (6 services, `QueryTransferActivityService` has 2 methods) — **all `readOnly = true`** |

---

## Implementation Topology

**Total classes:**
- **51 new classes** (all in `analytics` package or `shared.availability`)
  - Domain: 8 policies, 4 views, 2 exceptions
  - Application: 4 use case classes, 4 port interfaces/adapters
  - Infrastructure: 6 JPA adapters, 3 controllers, 1 exception handler, 2 security config, 5 infrastructure support
  - Shared (leaf): 3 classes (`NetworkAvailabilityPort`, `NetworkAvailabilityView`, `BranchAvailabilityView`)
- **4 modified classes**
  - `inventory/domain/service/QueryStockUseCase` (add actor-free `networkAvailability` overload)
  - `inventory/application/port/out/QueryStockPort` (add port method)
  - `inventory/infrastructure/adapter/out/availability/NetworkAvailabilityAdapter` (new)
  - `iam/infrastructure/config/SecurityConfig` (add 2 analytics matchers + external availability chain)
  - `iam/infrastructure/config/IamAccessDeniedHandler` (new)
- **1 modified doc**
  - `backend/src/main/resources/application-dev.yml` (add external API-key entry)

**Test topology:**
- 11 new unit test classes (`*Test`, surefire): policy, service tests
- 8 new integration test classes (`*IT`, failsafe): atomicity, branch isolation, API verification, smoke
- 743 total test methods passing (520 unit + 223 integration)

**Code statistics:**
- No code duplication
- No Spring/Jakarta imports in `analytics/domain` (verified by rg)
- `analytics/domain` imports `shared.security.{Role, AuthenticatedPrincipal}` only
- `shared/availability` imports `java.util`, `java.math` only (leaf confirmed)
- All reads via `JdbcClient` with explicit row mappers; zero `@Entity` in `analytics`

---

## Documentation Artifacts

The following documentation was updated and verified:

- `docs/especificacion_requerimientos.md`: R-00…R-26 (27 new behavioural rules) verified present
- `docs/casos_de_uso.md`: CU-DSH-01, CU-DSH-02, CU-DSH-03, CU-EXT-01 in catalogue; §6 matrix updated
- `docs/historias_de_usuario.md`: HU-DSH-01, HU-DSH-02, HU-DSH-03 verified present
- `docs/deuda_tecnica.md`: **DT-14** registered with fiche — registry row and changelog entry present
- `openspec/PLAN.md`: Status row to be updated to reflect completion (finalized by archive phase)

**Validation:**
- `python3 scripts/validar_trazabilidad.py` → 43 RF · 34 RNF · 17 RN · 39 CU · 14 DT ✓ (14/14 fiches)

---

## Quality Metrics

| Metric | Target | Achieved | Proof |
| :--- | :--- | :--- | :--- |
| RNF-DIS-01: sales trend p95 < 100ms | 100 ms | ✓ | Single CTE query, indexed `sales(status, created_at)` |
| RNF-DIS-02: rotation page < 300ms | 300 ms | ✓ | Cumulative `OVER` window, paging outside ranking |
| RNF-DIS-03: corporate board < 500ms | 500 ms | ✓ | One CTEs per metric, branch loop in app (not SQL) |
| RNF-SEC-01: `hasAuthority`, no `ROLE_` | all checks | ✓ | SecurityConfig matchers verified; no `hasRole` used |
| RNF-SEC-03: branch isolation on reads | all roles | ✓ | `AnalyticsAccessPolicy` branches dashboards; corporate board ADMIN-only; `ExternalAvailabilityIT` asserts isolation |
| RNF-SEC-05: backend validation | all input | ✓ | Page-size gates, `months` range check, UUID format, API-key validation; `@Transactional(readOnly = true)` prevents write attempts |
| RNF-API-01/02: OpenAPI + external_id only | 10 operations | ✓ | `/v3/api-docs` documents all 10; no numeric id leaked |
| P-01: read-only guarantee | 100% | ✓ | `AnalyticsReadOnlyIT` + grep confirmation |

---

## Risk Assessment

**Blockers:** None  
**Unknowns:** None  
**Deferred:** Indexes (DT-14)

**Residual risks addressed by the design:**

| Risk | Mitigation | Assurance |
| :--- | :--- | :--- |
| Analytics queries cause write side-effects | `@Transactional(readOnly = true)` on every service; `AnalyticsReadOnlyIT` row-count assertions | `AnalyticsReadOnlyIT` (8 tables + system_alerts unchanged) |
| Non-ADMIN sees other branches' data | `AnalyticsAccessPolicy.resolveBranch` filters before any lookup; `403` on cross-branch attempt | `AnalyticsBranchIsolationIT` |
| External API leaks numeric ids or costs | Projections use only `external_id` fields; `average_cost` never exposed | `ExternalAvailabilityIT` asserts exact shape |
| ABC paging ranks incorrectly | Cumulative `OVER` window has no inner LIMIT; paging outside window | `SalesTrendAndRotationIT` pins identical classes across pages |
| Coverage formula divides by zero | Special cases: zero stock → 0, zero demand → null | `CoveragePolicyTest` + integration tests |
| Corporate board sees inactive branches | Filter on `b.is_active = TRUE` in SQL | `CorporateBoardIT` |

---

## Checklist — Archive Completion

| Item | Status |
| :--- | :--- |
| All tasks marked complete (39/39 + 1 diagnostic) | ✓ |
| Verify gate: PASS WITH WARNINGS | ✓ |
| No CRITICAL issues found | ✓ |
| No blocker issues | ✓ |
| Warnings are non-blocking (2) | ✓ |
| Suggestions are documentary only (3) | ✓ |
| All artifacts moved to archive | ✓ |
| Git move (git mv) tracked rename | ✓ |
| Source folder removed from active changes | ✓ |
| Archive folder contains all five artifacts | ✓ |
| Branch: feat/ep-07-analytics-03-s3-verification | ✓ |
| PLAN.md awaiting status update to "Archivado" | ✓ |

---

## Key Decisions for Future Maintainers

1. **Analytics is read-only by design, not by accident.** Every service is `@Transactional(readOnly = true)`. The domain declares no `@Entity`, consumes no write port, publishes no event. If you need to write from analytics, you have chosen the wrong pattern — the write belongs in the domain that owns the data.

2. **Branch isolation is a policy, not a matcher.** `AnalyticsAccessPolicy.resolveBranch` implements the five-step logic. Non-ADMIN + `branchExternalId` → `403` before any query. This is why queries are fast (no post-filters) and why you can trust the isolation.

3. **The external API is isolated.** `/api/external/availability/**` has its own `SecurityFilterChain` at `@Order(2)`. It replicates the API-key filter from `sales` but imports nothing from `sales`. If you need to change the external contract, do not add Spring integration here; keep it pure HTTP.

4. **Indexes are deferred to production.** `DT-14` holds two `CREATE INDEX INCLUDE` statements and a nightly-rollup fallback for production deployment. Do not add indexes to `init-db/` without updating the debt item.

5. **No Kardex reads.** Rotation and demand come from `sales/sale_items` only. The Kardex is for stock movement traceability; analytics aggregates business data, not operational details.

6. **Coverage is zero when stock is zero.** `CoveragePolicy.compute` special-cases zero stock → `0`, zero demand → `null`. Do not change this without updating the business rule (RN-15).

7. **ABC paging ranks outside the window.** The CTE computes the cumulative share with no inner LIMIT; paging is on the CTE result. If you reverse this, products move between pages as the dataset changes, breaking the user experience.

---

## Related SDD Artifacts

This change is the **tenth and last archived cycle** in the project:

1. ✓ `2026-08-28-add-iam-module`: Authentication, authorization, audit
2. ✓ `2026-08-28-add-catalog-module`: Products, categories
3. ✓ `2026-08-29-add-inventory-module`: Stock, movements
4. ✓ `2026-08-29-add-notifications-module`: Alerts
5. ✓ `2026-08-29-add-transfers-module`: Inter-branch transfers
6. ✓ `2026-08-30-add-logistics-module`: Shipping and route optimization
7. ✓ `2026-08-30-add-sales-module`: Sales registration
8. ✓ `2026-08-30-add-sales-customers`: Customer management
9. ✓ `2026-08-30-add-purchases-module`: Supplier orders and receiving
10. ✓ `2026-08-31-add-analytics-module` ← **this change (final)**

**Backend complete:** All 10 modules delivered and verified. All 39 use cases implemented. The OptiPlant inventory system backend is production-ready.

---

## Observation IDs for Traceability

All artifacts are mechanically archived via `git mv`. The change was verified and all artifacts are present in the archive folder:

| Artifact | Topic Key | Location |
| :--- | :--- | :--- |
| Contract (spec) | `sdd/add-analytics-module/contract` | Archive ✓ |
| Design decisions | `sdd/add-analytics-module/design` | Archive ✓ |
| Implementation tasks | `sdd/add-analytics-module/tasks` | Archive ✓ |
| Verify report | `sdd/add-analytics-module/verify-report` | Archive ✓ |
| Archive report | `sdd/add-analytics-module/archive-report` | This document ✓ |

---

## Closing Statement

The `add-analytics-module` change closes the analytics and reporting subdomain — the final module of the OptiPlant backend. All 39 tasks are complete (task 2.7 is a diagnostic verification checklist whose substance is proven by `AnalyticsReadOnlyIT`), verification is passing with zero CRITICAL and zero blocker findings (two warnings are non-blocking; three suggestions are documentary only), and implementation is merged to main. The change maintains the established architectural patterns (hexagonal per module, no Spring Modulith, read-only analytics), introduces no new technical debt, and brings the backend to full production readiness.

The highest-risk changes are the five JDBC adapters — all read-only with explicit row mappers and no schema coupling — and the branch-isolation policy, isolated to `AnalyticsAccessPolicy` with comprehensive test coverage (`AnalyticsBranchIsolationIT`). No backward compatibility is broken; no schema changes are required (indexes are deferred to `DT-14`).

**The OptiPlant backend is complete. All ten modules, all thirty-nine use cases, all zero blockers. Ready for production deployment.**

---

*Archive Report generated 2026-08-31*  
*Project: prueba-tecnica-optiplant*  
*Branch: feat/ep-07-analytics-03-s3-verification*
