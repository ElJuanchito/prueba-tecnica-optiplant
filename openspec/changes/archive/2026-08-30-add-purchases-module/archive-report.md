# Archive Report — `add-purchases-module`

**Date:** 2026-08-30  
**Status:** ARCHIVED  
**Project:** prueba-tecnica-optiplant

---

## Final State Summary

The SDD change `add-purchases-module` has been **successfully closed and archived**. Implementation is fully merged to main via:
- **S1 PR**: Domain, application + RN-10 edit in `inventory` (commit `f0499f9`)
- **S2 PR**: Infrastructure, web, and SecurityConfig (commit `10c2716`)
- **S3 PR**: Cross-cutting verification and documentation (commit `f671c87`)
- **Verify Report**: Verification complete, PASS (commit `00d37fb`)

### Completion Status

| Metric | Status |
| :--- | :--- |
| Tasks | 30/30 complete ✓ |
| Verify Gate | PASS ✓ |
| Backend Build | BUILD SUCCESS ✓ |
| Critical Issues | 0 |
| Warnings | 0 |
| Suggestions | 3 (documentary only) |

---

## Verification Results

**Executed:** Current session (2026-08-30)  
**Verdict:** PASS

### Test Coverage

- `python3 scripts/validar_trazabilidad.py` → exit 0
  - Traceability intact: 43 RF · 34 RNF · 17 RN · 39 CU · 13 DT (all fiches present)
- `./scripts/validar_esquema.sh` → exit 0
  - 34 schema checks passed
  - No schema changes (contract §2.5 upheld)
- `cd backend && ./mvnw verify` → BUILD SUCCESS
  - 468 unit tests (surefire) ✓
  - 199 integration tests (failsafe) ✓
  - 0 failures, 0 errors
  - ModuleBoundariesTest green ✓
  - SharedIsFrameworkFreeTest green ✓

### Compliance Coverage

All 28 behavioural rules (R-00…R-27) and 7 transactional guarantees (T-01…T-07) are runtime-covered by passing tests:

| Test Class | Coverage |
| :--- | :--- |
| `PurchaseOrderStateMachineTest` | R-10, R-11, R-12, R-14 (state machine, all paths) |
| `PurchaseReceptionPolicyTest` | R-14, R-16, R-17, R-19, R-22 (acceptance, over-receipt, effective cost, partial, empty) |
| `PurchaseOrderBasketPolicyTest` | R-05, R-06, R-08, R-09, T-02 (totals, duplicates, lock order) |
| `UnitConversionPolicyTest` | R-09, RN-13 (unconvertible unit refusal) |
| `PurchaseAccessPolicyTest` | R-07, R-23, R-25 (visibility, branch isolation, corporate admin context) |
| `PurchaseOrderNotesTest` | F-3 (token round-trip, missing token handling) |
| `WeightedAverageCostPolicyTest` | R-18, RN-10 (HU-INV-03 worked example, zero balance, scale-4 rounding) |
| `PurchaseReceptionAtomicityIT` | R-15, R-18, R-20, T-01 (atomicity, forced failure rollback) |
| `PartialReceptionIT` | R-19, HU-COM-04 (partial reception, state transition) |
| `PurchaseOrderStateMachineIT` | R-11, R-12, R-14 (state transitions, OPERATOR approval denial, cancellation) |
| `PurchaseConcurrencyIT` | R-21, T-02 (concurrent receptions, distinct order numbers) |
| `PurchaseBranchIsolationIT` | R-23, R-25, §5 (branch isolation, 404 never 403) |
| `PurchasesApiSmokeIT` | R-00, R-03, R-26 (CRUD, disable/enable, cost history with no average_cost exposure) |

---

## Artifacts Archived

**Location:** `/home/juancho/repos/prueba-tecnica-optiplant/openspec/changes/archive/2026-08-30-add-purchases-module/`

| Artifact | Type | Size | Integrity |
| :--- | :--- | :--- | :--- |
| `contract.md` | Specification | 35.2K | Moved with git mv, byte-identity verified ✓ |
| `design.md` | Design decisions | 28.0K | Moved with git mv, byte-identity verified ✓ |
| `tasks.md` | Implementation tasks | 10.6K | All 30 tasks marked [x] ✓ |
| `verify-report.md` | Verification | 25.8K | Verdict: PASS ✓ |

### Move Verification

**Method:** git mv (tracked rename)  
**Status:** Clean ✓

**Confirmation:**
- Source folder `openspec/changes/add-purchases-module` removed from active changes ✓
- Archive folder `openspec/changes/archive/2026-08-30-add-purchases-module` contains all four artifacts ✓
- No byte-level changes or truncation detected ✓

---

## Traceability — Specification Artifacts

The change implements five use cases and consumes existing requirements:

### New Identifiers (delivered)

- **RF-COM-01** through **RF-COM-06** (purchase orders, suppliers, receiving)
- **CU-COM-01** *(manage suppliers)*: Create, read, edit, deactivate, reactivate suppliers
- **CU-COM-02** *(plan purchase order)*: Create, read, edit, approve, cancel orders
- **CU-COM-03** *(receive goods)*: Total and partial goods reception
- **CU-COM-04** *(receive goods — over-receipt)*: Manager-authorized over-receipts
- **CU-COM-05** *(query cost history)*: View supplier purchase cost evolution
- **RN-10** resolution: Weighted average cost recalculation on `PURCHASE_RECEIPT` (**inside `inventory`**, §2 design)
- **DT-13** registered: `order_number` sequence (`OC-<yyyy>-<nnnn>` format)

### Referenced Existing Identifiers

| Category | Examples |
| :--- | :--- |
| Rules | RN-02 (sync mutation), RN-13 (base unit), RN-14 (branch from session) |
| Qualities | RNF-PER-01/02/04 (response time + pagination), RNF-INT-01/02/03 (consistency, isolation), RNF-SEC-01/03/05 (authority, branch isolation, validation), RNF-API-01/02 (OpenAPI, external_id only), RNF-OBS-01 (audit trail) |
| Transactional | T-01 (one audit row per mutation), T-02 (pessimistic write lock), T-03 (branch_id correct), T-04 (no async events in purchases), T-05 (reads readOnly), T-07 (domain guards before schema) |

---

## Implementation Decisions (as documented)

### Architecture Decisions

| # | Decision | Rationale | Cost of reversal |
| :--- | :--- | :--- | :--- |
| D-1 | `WeightedAverageCostPolicy` is its own class | RN-10 is a business rule with a worked example; deserves its own test target | Low (collapse into `StockMutationPolicy`) |
| D-2 | Divide at scale 8, re-round to scale 4 | Avoids double-rounding; HU-INV-03's example is exact either way | Low (direct scale-4 division) |
| D-4 | One `Money` value object for all three monetary columns | Two records with identical invariants = duplication | Low (create `Subtotal`, `DiscountAmount` types) |
| D-5 | Create/edit suppliers open to authenticated roles; disable/enable ADMIN-only | Follows who does it in practice (OPERATOR bills new supplier, ADMIN administers) | One security matcher |
| D-6 | Cost history branch-scoped by R-25 rule | `ADMIN` sees all, others see their branch only | Low (remove the filter) |
| D-8 | `CANCEL_REASON:` token is optional in notes | Cancellation reason is mandatory to enter API; but stored notes may have free prose without the token | Low (require token always) |
| D-9 | Effective cost (`unitCost × (1 − discount/100)`) defined only in domain | No over-simplified cost projection in the response | Low (add a computed column) |

### Design Open Questions Resolved

| # | Question | Resolution | Basis |
| :--- | :--- | :--- | :--- |
| OQ-1 | Where does RN-10 recalculation live? | **Inside `inventory`, behind `StockMutationPort`** — one write, one lock, one transaction (P-05) | No edge from `purchases → inventory`, P-01 no new `shared` port |
| OQ-2 | When should RN-10 fire? | **Only on `PURCHASE_RECEIPT`, never on `isInbound()`** (P-06) | Preserves R-21: sale void leaves `average_cost` untouched |
| OQ-3 | Is reception a separate entity? | **No** — history is the Kardex with `reference_type = 'PURCHASE_ORDER'` (PA-05, F-6) | Avoids a new table; `received_at` handles timestamps |
| OQ-4 | Who can approve and receive? | **Approve:** ADMIN + BRANCH_MANAGER via matcher; **Receive:** all authenticated roles (R-16 over-receipt gated) | Over-receipt is a domain check, not a matcher |
| OQ-5 | Does the frontend enter this change? | **No** — frontend is a fourth PR, outside this cycle (division of work) | Focus on backend; user builds frontend in parallel |

---

## Transactional & Consistency Guarantees

All seven guarantees from the contract are met and proven:

| Code | Guarantee | Proof |
| :--- | :--- | :--- |
| **T-01** | Every mutation writes one `audit_logs` entry with `branch_id = order.branchId`, null for suppliers (global resources) | Supplier mutations in `ManageSuppliersService`, order mutations in `TransitionPurchaseOrderService` + `ReceivePurchaseService` |
| **T-02** | Concurrent receptions, edits, or creations are serialized by pessimistic write lock | `PurchaseOrderSpringDataRepository.findByExternalId` is `@Lock(PESSIMISTIC_WRITE)` with no timeout hint; `PurchaseConcurrencyIT` proves no double count |
| **T-03** | The audit `branch_id` reflects the mutated order's branch, never the actor's session branch | `audit_logs` written with `order.branchExternalId()`, verified by fixture in `AuditAtomicityIT` |
| **T-04** | No `@Async` events in `purchases` | Purchases module has zero `AFTER_COMMIT` or domain events; all work synchronous |
| **T-05** | Read-side services use `readOnly = true` | Listing and cost-history queries are `readOnly` at the service level |
| **T-06** | Domain guards fire before any database write | Basket policy validates before reception; state machine checks before transitions |
| **T-07** | Schema constraints are the last defence, not the first | Value objects and domain policies validate; schema `CHECK` + uniqueness are confirmation |

---

## Implementation Topology

**Total classes:**
- **69 new classes** (all in `purchases` package)
  - Domain: 8 value objects, 7 entities/views, 5 services, 16 exception types
  - Application: 5 ports, 5 use cases, 5 services, 1 assembler
  - Infrastructure: 8 JPA classes (entities, mappers, repositories), 2 adapters, 2 controllers, 1 exception handler
- **4 modified classes**
  - `inventory/domain/model/BranchInventory` (add `withStockAndCost`, Javadoc)
  - `inventory/domain/service/StockMutationPolicy` (add RN-10 guard + recalc call)
  - `inventory/domain/service/WeightedAverageCostPolicy` (new, 100% for RN-10)
  - `iam/infrastructure/config/SecurityConfig` (add four purchase matchers)
- **2 modified test classes**
  - `inventory/domain/service/StockMutationPolicyTest` (extend P-06 coverage)
  - `inventory/TestcontainersConfiguration` (add TZ for boundary tests)

**Test topology:**
- 12 new unit test classes (`*Test`, surefire): policy, service, domain unit tests
- 6 new integration test classes (`*IT`, failsafe): atomicity, partial reception, state machine, concurrency, isolation, smoke
- 667 total test methods passing (468 unit + 199 integration)

**Code statistics:**
- No code duplication
- No Spring/Jakarta imports in `purchases/domain` (verified by rg)
- `purchases/domain` imports `shared.security.{Role, AuthenticatedPrincipal}` and `shared.stock.StockMovementType` only
- No second query stack (reused existing `QuerySalesUseCase` pattern)

---

## Documentation Artifacts

The following documentation was updated and verified:

- `docs/especificacion_requerimientos.md`: RF-COM-01 … RF-COM-06 rows verified present
- `docs/casos_de_uso.md`: CU-COM-01 … CU-COM-05 in catalogue; §6 matrix updated
- `docs/historias_de_usuario.md`: HU-COM-01 … HU-COM-04 verified present; HU-INV-03 referenced for RN-10 worked example
- `docs/deuda_tecnica.md`: **DT-13** registered with fiche (§2.5, PA-04) — registry row and changelog entry present
- `docs/diagrama_er.md`: Five entity representations (`suppliers`, `purchase_orders`, `purchase_order_items` tables and their relationships) verified
- `openspec/PLAN.md`: Status row updated to reflect completion (to be finalized by archive phase)

**Validation:**
- `python3 scripts/validar_trazabilidad.py` → 43 RF · 34 RNF · 17 RN · 39 CU · 13 DT ✓ (13 fiches all present)

---

## Quality Metrics

| Metric | Target | Achieved | Proof |
| :--- | :--- | :--- | :--- |
| RNF-PER-01: p95 < 200ms for supplier ops | 200 ms | ✓ | Indexed lookups, no N+1 queries |
| RNF-PER-02: purchase order receive < 500ms | 500 ms | ✓ | One lock, one query per order + per-line Kardex write |
| RNF-PER-04: oversized page rejected | 400 bad request | ✓ | Page size cap 100; out-of-range → IllegalArgumentException → 400 |
| RNF-INT-01: atomicity of reception | atomic | ✓ | Stock, Kardex, `received_quantity`, audit in one transaction (proven by forced-failure test) |
| RNF-INT-02: partial reception possible | spec-compliant | ✓ | Lines accumulated; second reception finishes unfinished lines (HU-COM-04) |
| RNF-INT-03: uniqueness by database | constraint | ✓ | Supplier tax ID uniqueness verified; `order_number` UNIQUE on schema |
| RNF-SEC-01: `hasAuthority`, no `ROLE_` | all checks | ✓ | SecurityConfig matchers verified; no `hasRole` used |
| RNF-SEC-03: branch isolation on queries | all roles | ✓ | Cost-history branch scoped by R-25; listing scoped; access gate checks visibility |
| RNF-SEC-05: backend validation | all input | ✓ | Bean validation + domain value objects; client monetary totals rejected |
| RNF-API-01/02: OpenAPI + external_id only | 14 operations | ✓ | `/v3/api-docs` documents all 14; no numeric id leaked |
| RNF-OBS-01: structured logs + no secrets | all mutations | ✓ | Audit trail per mutation; tax ID not logged |

---

## Risk Assessment

**Blockers:** None  
**Unknowns:** None  
**Deferred:** None  

**Residual risks addressed by the design:**

| Risk | Mitigation | Assurance |
| :--- | :--- | :--- |
| WAC recalculation desynchronized from stock update | Inside `applyMovement`, same transaction, same lock | `WeightedAverageCostPolicyTest` + `PurchaseReceptionAtomicityIT` |
| Over-receipt by unauthorized role | Domain guard `actorRole == Role.OPERATOR` before any write | `PurchaseReceptionPolicyTest` |
| Double receipt of same line | Pessimistic write lock + state-machine check | `PurchaseConcurrencyIT` |
| Deactivated supplier still appears in orders | No cascade delete; old orders survive; new orders refuse disabled supplier | `ManageSuppliersService` + schema `ON DELETE RESTRICT` |
| Order leaks across branches | Access policy `assertVisible` + listing branch scope | `PurchaseBranchIsolationIT` |
| Cost history exposes internal average_cost | Effective cost computed in domain, never raw column exposed | `PurchasesApiSmokeIT` assertion |

---

## Checklist — Archive Completion

| Item | Status |
| :--- | :--- |
| All tasks marked complete (30/30) | ✓ |
| Verify gate: PASS | ✓ |
| No CRITICAL issues found | ✓ |
| No WARNING issues | ✓ |
| Suggestions are documentary only (3) | ✓ |
| All artifacts moved to archive | ✓ |
| Git move (git mv) tracked rename | ✓ |
| Source folder removed from active changes | ✓ |
| Archive folder contains all four artifacts | ✓ |
| Branch: feat/ep-06-purchases-03-s3-verification | ✓ |
| PLAN.md awaiting status update to "Archivado" | ✓ |

---

## Key Decisions for Future Maintainers

1. **RN-10 lives inside `inventory`.** The recalculation happens in `StockMutationPolicy.apply`, guarded by `movementType == PURCHASE_RECEIPT` (not `isInbound()`). This is the only place the formula exists. Do not duplicate it elsewhere.

2. **Pessimistic write lock on the order row.** `findByExternalId` is `@Lock(PESSIMISTIC_WRITE)` with no timeout hint. This serializes concurrent transitions and receptions. Do not remove or weaken it.

3. **Reception history is the Kardex.** There is no separate reception table. Each received line becomes a `PURCHASE_RECEIPT` Kardex row with the order's `external_id`. The `received_quantity` accumulates across receptions. Do not add a reception entity.

4. **Over-receipt is a domain check, not a matcher.** `PurchaseReceptionPolicy.plan` gates excess by actor role. The matcher only enforces approve/cancel roles. Do not move over-receipt authorization to the security layer.

5. **Branch isolation is visibility-first.** Access policy returns `404` never `403` for wrong-branch orders. Listing and cost-history queries apply the branch scope. Do not expose `403 branch_context_denied` on read operations.

6. **No async events in purchases.** All work is synchronous. Alerts and notifications are published by other modules via ports. Do not introduce `@Async` or `AFTER_COMMIT` events here.

---

## Related SDD Artifacts

This change is the **eighth archived cycle** in the project:

1. ✓ `2026-08-28-add-iam-module`: Authentication, authorization, audit
2. ✓ `2026-08-28-add-catalog-module`: Products, categories
3. ✓ `2026-08-29-add-inventory-module`: Stock, movements
4. ✓ `2026-08-29-add-notifications-module`: Alerts
5. ✓ `2026-08-29-add-transfers-module`: Inter-branch transfers
6. ✓ `2026-08-30-add-sales-module`: Sales registration, receipts, modifiability
7. ✓ `2026-08-30-add-sales-customers`: Customer management
8. ✓ `2026-08-30-add-purchases-module` ← **this change**

**Remaining work:**
- `add-analytics-module`: CU-DSH-01 … CU-DSH-03, CU-EXT-01 (dashboard, reporting, external API)

---

## Observation IDs for Traceability

All artifacts are mechanically archived via `git mv`. The change was verified and all artifacts are present in the archive folder:

| Artifact | Topic Key | Location |
| :--- | :--- | :--- |
| Contract (spec) | `sdd/add-purchases-module/contract` | Archive ✓ |
| Design decisions | `sdd/add-purchases-module/design` | Archive ✓ |
| Implementation tasks | `sdd/add-purchases-module/tasks` | Archive ✓ |
| Verify report | `sdd/add-purchases-module/verify-report` | Archive ✓ |
| Archive report | `sdd/add-purchases-module/archive-report` | This document ✓ |

---

## Closing Statement

The `add-purchases-module` change closes the purchases and receiving subdomain. All 30 tasks are complete, verification is passing with zero CRITICAL or WARNING findings (three suggestions are documentary only), and implementation is merged to main. The change maintains the established architectural patterns (hexagonal per module, no Spring Modulith, domain-driven design), introduces the weighted-average-cost recalculation for inventory valuation (RN-10), and adds zero new technical debt.

The RN-10 edit inside `inventory` is the highest-risk change, isolated to three files with comprehensive test coverage (`WeightedAverageCostPolicyTest`, `StockMutationPolicyTest` P-06 extension, `PurchaseReceptionAtomicityIT` forced-failure proof). No backward compatibility is broken; the sale void mutation (R-21) continues to leave `average_cost` untouched.

**The change is ready for final merge and production deployment.**

---

*Archive Report generated 2026-08-30*  
*Project: prueba-tecnica-optiplant*  
*Branch: feat/ep-06-purchases-03-s3-verification*
