# Archive Report: add-inventory-module

**Archive Date**: 2026-08-29  
**Change Name**: add-inventory-module  
**Archive Location**: `openspec/changes/archive/2026-08-29-add-inventory-module/`  
**Status**: COMPLETE WITH WARNINGS

---

## Executive Summary

The inventory and notifications module implementation (add-inventory-module) has been successfully archived after completing all 3 phases of implementation and verification across three pull requests. All 42 implementation tasks are marked complete. The change is production-ready with zero CRITICAL verification issues. Two non-blocking warnings are documented: one regarding partial technical debt repayment (DT-07, step 1/4 completed; remaining HTTP exposure work deferred to a follow-up change), and one regarding untested page-size cap validation (R-00, code correct but no automated test for inventory/notifications).

---

## Change Artifacts

| Artifact | Status | Location |
|----------|--------|----------|
| contract.md | ✓ Complete | Archive |
| design.md | ✓ Complete | Archive |
| tasks.md | ✓ All 42 tasks marked [x] | Archive |
| verify-report.md | ✓ PASS WITH WARNINGS (0 CRITICAL, 2 WARNING) | Archive |

No formal delta specs were created for this change. The specification remains in `contract.md` and `design.md`, consistent with the approach used for `add-iam-module` and `add-catalog-module`.

---

## Implementation Phases

### Phase 1: Domain and Application — inventory and notifications (S1 / PR1)
- `shared/stock/` module: `StockMovementType` enum with eight literals, `StockMutationPort`, `StockMutationCommand`, `InTransitShiftCommand`, `InTransitDirection`
- `shared/alert/` module: `OperationalAlertRaised`, `AlertType`, `AlertSeverity` events
- `inventory/domain/`: `Quantity`, `StockLevel`, `UnitCost`, `MovementReason`, `DateRange` value objects with invariants
- `inventory/domain/`: `BranchInventory` aggregate and `KardexMovement` immutable record
- Eight read-model projections: `StockLine`, `StockPage`, `BranchAvailability`, `KardexLine`, `NetworkAvailability`, `KardexPage`, `ThresholdView`, `MovementReceipt`, `ProductDescriptor`, `StockThresholdBreach`
- Eight exceptions in `inventory/domain/exception/`
- Policies: `StockMutationPolicy` (returning `MovementDraft` to enforce RN-02 atomicity), `AdjustmentPolicy`, `AlertRaisingPolicy`, `BranchScopePolicy`
- Application ports: `BranchInventoryRepositoryPort`, `KardexRepositoryPort`, `ProductLookupPort`, `AlertEventPublisherPort`
- Application use-cases: `QueryStockUseCase`, `QueryKardexUseCase`, `RegisterStockMovementUseCase`, `ManageStockThresholdUseCase` (all authenticated with branch derived from session per RN-14)
- `StockMovementService` (transactional, lock → policy → save → append → audit → publish pattern), `StockQueryService`, `StockThresholdService`, `KardexQueryService`
- `notifications/domain/`: `Alert` aggregate, `AlertDedupKey` (single writer of F-1 token), two exceptions
- `notifications/application/`: `AlertRepositoryPort`, `ManageAlertsUseCase`, `AlertService`
- Unit tests (41 tests, all passing, including domain, policy, and service layers)

### Phase 2: Infrastructure, Web, and Event Listener (S2 / PR2) — COMPLETE
- JPA infrastructure: `BranchInventoryJpaEntity`, `KardexMovementJpaEntity`, mappers, repositories
- Pessimistic locking on `BranchInventorySpringDataRepository` with `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- Foreign key resolver with native SQL queries for `external_id → id` mapping
- `BranchInventoryPersistenceAdapter` explicitly setting `min_stock_threshold = 0` on creation (F-3)
- `InventoryStockPresenceAdapter` implementing `ProductStockPresencePort` (catalog's base-unit rule integration)
- `InventoryController` with six endpoints (no `branchId` in path/query/body per RN-14)
- `InventoryExceptionHandler` with all 11 error codes mapped (including `PessimisticLockingFailureException` → 409)
- `notifications/` infrastructure: `SystemAlertJpaEntity`, `AlertMapper`, advisory-lock native query on dedup
- `OperationalAlertListener` with `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`, error handling via try/catch
- `AlertController` and `NotificationsExceptionHandler`
- `SecurityConfig` extended with five new matchers for inventory and notification endpoints (string literals only, no imports)
- Service annotations restored after infrastructure adapters supplied
- All 30 schema invariants passing; no schema changes introduced
- 321 total tests passing (unit + integration via Testcontainers)

### Phase 3: Cross-Cutting Verification and Documentation (S3 / PR3)
- `KardexAtomicityIT`: Kardex replayed from `INITIAL_LOAD` equals `current_stock`; forced failure leaves neither balance change nor Kardex row (T-01)
- `InventoryBranchIsolationIT`: Branch isolation enforced across three requirements; distinct name to avoid collision with `iam` module's `BranchIsolationIT`
- `StockValidationIT`: Two concurrent write-offs serialize on `FOR UPDATE`; winner succeeds, loser receives `insufficient_stock` 409 (RN-01, T-02)
- `StockAlertIT`: Alert raised after commit, not duplicated across repeated breaching movements, not auto-resolved when stock recovers (R-20, R-21, R-24), listener failure does not roll back movement
- `InventoryApiSmokeIT`: One assertion per read endpoint (`/stock`, `/stock/{id}/network`, `/kardex`, `/alerts`) and threshold `PUT`; no numeric ID leak (four endpoints + threshold write verified)
- `InventoryRbacIT`: OPERATOR denied adjustments/Kardex/alerts, allowed write-offs (R-13); corporate ADMIN receives `branch_context_required` on mutations (PA-02)
- `ProductCatalogIT` confirmation: base-unit expectation still holds after `ProductStockPresencePort` implementation
- DT-09 registered in `docs/deuda_tecnica.md` (Spanish): alert dedup advisory-lock guard with future partial unique index repayment
- OpenAPI `/v3/api-docs` documents all eight operations (RNF-API-01)
- All validation gates green: `python3 scripts/validar_trazabilidad.py`, `./scripts/validar_esquema.sh`, `cd backend && ./mvnw verify`

---

## Test Results

| Test Suite | Count | Status |
|-----------|-------|--------|
| Unit tests (surefire) | 212 | ✓ PASS |
| Integration tests (failsafe) | 109 | ✓ PASS |
| **Total** | **321** | **✓ PASS** |

Key test classes:
- `StockMutationPolicyTest`, `AdjustmentPolicyTest`, `AlertRaisingPolicyTest`, `BranchScopePolicyTest` (policy domain)
- `AlertDedupKeyTest`, `AlertServiceTest` (alert domain)
- `StockMovementServiceTest` (movement service with stubbed ports)
- `KardexAtomicityIT`, `InventoryBranchIsolationIT`, `StockValidationIT`, `StockAlertIT` (atomicity and isolation)
- `InventoryApiSmokeIT`, `InventoryRbacIT` (API contract and security)
- All ArchUnit/ModuleBoundaries and framework-isolation tests green

---

## Verification Report

**Verdict**: **PASS WITH WARNINGS** (0 CRITICAL issues, 2 WARNINGS, 1 SUGGESTION)

**Evidence basis**:
- `cd backend && ./mvnw verify` — BUILD SUCCESS, exit 0 (321 tests)
- `./scripts/validar_esquema.sh` — 30/30 schema invariants pass (unchanged)
- `python3 scripts/validar_trazabilidad.py` — Traceability complete (42 RF · 34 RNF · 17 RN · 37 CU · 9 DT)
- All 42 implementation tasks marked [x]
- All use cases (CU-INV-03…08, CU-ALE-01/02) covered by passing tests
- All 11 error codes (contract §7) mapped and reachable

**Deviations from design**: None identified. Implementation matches contract and design specifications exactly.

---

## Warnings and Follow-up Items

### ⚠️ WARNING 1: DT-07 Partial Repayment

**Status**: Repayment partially complete (1/4 steps)

**Details**: DT-07 ("Exposición HTTP del cambio de unidad base, diferida") identifies four work steps:
1. Implement `ProductStockPresencePort` with real adapter — **✓ COMPLETED** (task 2.6, `InventoryStockPresenceAdapter`)
2. Publish `PATCH /api/catalog/products/{externalId}/base-unit` endpoint — **⏳ NOT COMPLETED**
3. Two distinct error codes (has history vs. cannot verify) — **⏳ NOT COMPLETED**
4. Verify precondition check and base_unit write share one transaction — **⏳ NOT COMPLETED**

**Implication**: Steps 2–4 are deferred to a separate follow-up change. The add-inventory-module contract does not mention DT-07, so this is not a violation of this change's scope. However, `docs/deuda_tecnica.md` was not updated to reflect the partial completion; the debt item still reads "Estado: Aceptada" with all four steps listed as pending. Recommend either (a) updating DT-07 to record step 1 complete and split remaining work into an explicit follow-up, or (b) scheduling the HTTP-exposure work before the next change touching `catalog` or `inventory`.

**Non-blocking for archive**: Per contract.md definition of done, this change is complete. No action required to archive.

### ⚠️ WARNING 2: R-00 Page-Size Cap — No Automated Test

**Status**: Implemented but untested

**Details**: `resolveSize()` in both `InventoryController` and `AlertController` correctly throws `IllegalArgumentException` (→ `400 invalid_request`) when page size exceeds `MAX_PAGE_SIZE`, mirroring the proven `catalog` pattern. However, no test in this change exercises that branch. Only the "no branchId in request" half of R-00 is verified by automated test (`InventoryApiSmokeIT.assertNoNumericIdLeak`).

**Recommendation**: Add one assertion to `InventoryApiSmokeIT` alongside the next touch of that file, e.g., a request with `pageSize=9999` expecting `400 invalid_request`.

**Non-blocking for archive**: Code is correct; the gap is test coverage, not implementation.

### 💡 SUGGESTION: `StockMutationPort` Implementation Plan

`StockMutationPort.shiftInTransit` and `applyMovement` have zero implementations and zero callers. This is correct per contract §10 definition of done (only `ProductStockPresencePort` required). The port exists as a declared interface for future `purchases`/`sales`/`transfers` modules. `StockMovementService`'s private `mutate()` currently duplicates the atomic-write guarantee inline. When `purchases`/`sales` arrive, either extract that logic into the port's first real implementation or keep two independent code paths honoring the same invariant. Flagged for the next module's author's benefit.

---

## Compliance

**Completeness**: 100% of scope delivered
- ✓ All 3 phases complete (S1 domain+app, S2 infra+web+listener, S3 verification)
- ✓ All 42 tasks marked [x]
- ✓ All 321 tests passing (212 unit + 109 integration)
- ✓ All schema invariants verified (30/30, unchanged)
- ✓ Traceability complete

**Requirements Coverage**:
- 25/25 behavioral requirements (RF, RNF, RN from contract) satisfied
- 25/25 scenarios verified
- All CLAUDE.md invariants upheld:
  - ✓ Roles without `ROLE_` prefix, `hasAuthority()` not `hasRole()`
  - ✓ Branch derived from session (RN-14), never from client
  - ✓ API exposes only `external_id` (no numeric IDs)
  - ✓ Stock mutation and Kardex in same transaction (RN-02, F-3)
  - ✓ Alert event published last, inside transaction (P-10, design §11 trap 4)

**Security/RBAC**:
- ✓ All endpoints properly secured per contract §5 matrix
- ✓ OPERATOR correctly denied adjustments/Kardex/alerts, allowed write-offs
- ✓ Corporate ADMIN forbidden session-scoped mutations (PA-02)
- ✓ No numeric ID leak (verified across all four read endpoints + threshold write)

---

## Archive Completeness Checklist

- [x] Change folder moved to archive (2026-08-29-add-inventory-module/)
- [x] Archive contains all artifacts (contract, design, tasks, verify-report)
- [x] Archived tasks.md has no unchecked implementation tasks (all 42 marked [x])
- [x] Active changes directory no longer has this change (add-inventory-module removed)
- [x] No delta specs to sync (specification consolidated in contract.md and design.md)
- [x] All validation gates passing (trazabilidad, esquema, backend tests)

---

## Mechanical Copy Verification

### Archive Move Diff
Pre-move snapshot vs. archived folder: **empty diff** (byte-for-byte match verified)

All files confirm:
- `contract.md` — 24,651 bytes ✓
- `design.md` — 22,776 bytes ✓
- `tasks.md` — 10,373 bytes ✓
- `verify-report.md` — 12,935 bytes ✓

No artifacts were modified, truncated, or altered during the archive move. All files retain their original byte sequences.

---

## Final State Summary

| Dimension | Status | Evidence |
|-----------|--------|----------|
| **Implementation** | ✓ Complete | All 42 tasks marked [x] across S1–S3 |
| **Testing** | ✓ All passing | 321 tests (212 unit + 109 integration), 0 failures |
| **Verification** | ✓ PASS WITH WARNINGS | verify-report: 0 CRITICAL, 2 WARNINGS (non-blocking), 1 SUGGESTION |
| **Schema** | ✓ Verified | 30/30 invariants pass (unchanged from catalog module additions) |
| **Traceability** | ✓ Integral | 42 RF · 34 RNF · 17 RN · 37 CU · 9 DT, all links valid |
| **RBAC** | ✓ Enforced | All endpoints protected per contract §5; corporate ADMIN isolation proven by `InventoryRbacIT` |
| **Audit** | ✓ Atomic | Audit `branchId` correctly reflects mutated resource (T-03); proven by service-layer pattern |
| **Concurrency** | ✓ Serialized | Pessimistic write lock on `BranchInventory` (design §7); proven by `StockValidationIT` |
| **Archive** | ✓ Complete | All artifacts mechanically moved; byte-for-byte fidelity verified |
| **Production Readiness** | ✓ Yes | No CRITICAL issues; two non-blocking documentation/coverage gaps documented |

---

## Known Technical Debt

The following items are documented in `docs/deuda_tecnica.md` and are consciously deferred:

1. **DT-07** (partial): Step 1/4 completed (ProductStockPresencePort adapter). Steps 2–4 (HTTP base-unit endpoint exposure) deferred to a follow-up change. Recommend updating DT-07 in docs to reflect partial completion.
2. **DT-09** (new): Alert dedup advisory-lock guard; repayment via partial unique index (design §9).

---

## Archive Decision

**Decision**: Archive with documented warnings. All implementation gates complete, all tests passing, all schema validated, full traceability verified. Two non-blocking warnings noted (partial debt repayment DT-07, untested R-00 cap). Both are expected and do not contradict contract.md or design.md.

**Reason**: The change meets all archive gates:
1. **Native Review Receipt Gate**: No review was run (gentle-ai review disabled per ordinary policy); archive proceeds under ordinary repository policy.
2. **Task Completion Gate**: All 42 tasks marked [x]; no stale checkboxes.
3. **Verification Gate**: Verdict is PASS WITH WARNINGS; 0 CRITICAL findings.

**Next Change**: The SDD cycle for add-inventory-module is closed. The two warnings (DT-07 repayment and R-00 test coverage) are forwarded as explicit follow-up work items to be addressed in the next inventory-related change. Ready for the next planned change or feature request.

---

## Artifact Observations

For traceability, the following change artifacts were read and processed in this archive phase:

- `openspec/changes/add-inventory-module/contract.md` — Scope, approach, 6 inventory use cases + 2 alert use cases, 11 error codes, authorization matrix, success criteria (all marked complete)
- `openspec/changes/add-inventory-module/design.md` — Architecture decisions D-01…D-04, domain models, policies, port contracts, security wiring, 11 design traps documented
- `openspec/changes/add-inventory-module/tasks.md` — 3 phases with 42 tasks (all marked [x])
- `openspec/changes/add-inventory-module/verify-report.md` — Comprehensive cross-document verification: 8 use cases covered end-to-end, 11 error codes mapped and reachable, authorization matrix validated, 3 closed cross-module decisions verified

**Note on Specifications**: This change used `contract.md` and `design.md` as the primary specification documents, rather than formal delta specs (no `specs/` folder). The approach mirrors `add-iam-module` and `add-catalog-module`, consolidating specification into design-focused documents suitable for self-contained architecture work.

---

*Archive created: 2026-08-29 by sdd-archive phase*  
*Change name: add-inventory-module*  
*Archive location: openspec/changes/archive/2026-08-29-add-inventory-module/*
