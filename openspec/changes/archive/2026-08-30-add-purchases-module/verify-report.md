```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:68ecfbdb416b3b21709b88e379ebd424fbd54a8d1d5f7cab43b8412b5984a8a8
verdict: pass
blockers: 0
critical_findings: 0
requirements: 28/28
scenarios: 0/0
test_command: cd backend && ./mvnw verify
test_exit_code: 0
test_output_hash: sha256:75ee605529addc6c044025eba35feba874eaf04d91ce236fb1f6d76e32acbab0
build_command: cd backend && ./mvnw verify
build_exit_code: 0
build_output_hash: sha256:75ee605529addc6c044025eba35feba874eaf04d91ce236fb1f6d76e32acbab0
```

# Verification Report — `add-purchases-module`

**Change**: `add-purchases-module` (branch `feat/ep-06-purchases-03-s3-verification`)
**Commits**: S1 `f0499f9` · S2 `10c2716` · S3 `f671c87`
**Mode**: full artifacts (contract + design + tasks), source inspection plus real execution evidence
**Verdict**: **PASS**

---

## 1. Task completeness

All 30 boxes in `tasks.md` are `[x]` — 14 in S1 (1.1–1.14), 8 in S2 (2.1–2.8), 8 in S3 (3.1–3.8).
Source inspection confirms every listed file exists with the described shape; no unchecked box, no
partially-done task, no task whose code state contradicts the checkmark.

| Slice | Boxes | State |
| :--- | :--- | :--- |
| S1 — RN-10 edit + `purchases` domain/application | 1.1–1.14 | all `[x]`, code present |
| S2 — infrastructure + web | 2.1–2.8 | all `[x]`, code present |
| S3 — cross-cutting verification + docs | 3.1–3.8 | all `[x]`, ITs present and green |

---

## 2. Command evidence

| Command | Result | Notes |
| :--- | :--- | :--- |
| `python3 scripts/validar_trazabilidad.py` | **RESULTADO: trazabilidad íntegra** | `43 RF · 34 RNF · 17 RN · 39 CU · 13 DT`; item 4 → `13 declarados, 13 con ficha`; 38 links, 0 broken. Exit 0. Matches the contract's pinned counts exactly. |
| `./scripts/validar_esquema.sh` | **RESULTADO: 34 comprobaciones correctas — esquema íntegro** | 21 tables, all A–H invariants green. Schema **unaffected** — contract §2.5 upheld. |
| `cd backend && ./mvnw verify` | **BUILD SUCCESS** (2:38 min) | Surefire `Tests run: 468, Failures: 0, Errors: 0, Skipped: 0`. Failsafe `Tests run: 199, Failures: 0, Errors: 0, Skipped: 0` — **failsafe == 199 as required**. `ModuleBoundariesTest` and `SharedIsFrameworkFreeTest` included and green. |
| `git diff main...HEAD -- backend/init-db/` | **empty (0 lines)** | Contract §2.5 / design §8 upheld: no schema file touched. |

The `ERROR`/`WARN` lines in the failsafe log (`AuditAtomicityFixtureService … Deliberate failure`,
`StockAlertIT … Deliberate … failure`, `numeric field overflow` in `PurchaseReceptionAtomicityIT`)
are the deliberate forced-failure fixtures of atomicity/rollback tests; every enclosing
`Tests run` line reports 0 failures and 0 errors.

New purchases ITs in the failsafe run: `PurchaseReceptionAtomicityIT` (2), `PartialReceptionIT` (1),
`PurchaseOrderStateMachineIT` (5), `PurchaseConcurrencyIT` (2), `PurchaseBranchIsolationIT` (5),
`PurchasesApiSmokeIT` (6).

---

## 3. Definition of Done — §10, the three PR checklists

### PR 1 — domain + application

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| `purchases.domain` with no Spring/Jakarta import; `shared` imports no module; `ModuleBoundariesTest` unchanged | **MET** | `rg "org.springframework|jakarta.persistence" purchases/domain` → nothing. `ModuleBoundariesTest` + `SharedIsFrameworkFreeTest` green in `verify`. `ModuleBoundariesTest.MODULOS` already lists `purchases` — no ArchUnit edit in the diff. |
| RN-10 recalculation lands inside `inventory` behind `StockMutationPort` (P-05), triggered only by `PURCHASE_RECEIPT` (P-06); no new `shared` port; `StockMutationPort` keeps two methods | **MET** | New `inventory/domain/service/WeightedAverageCostPolicy` (pure Java). `StockMutationPolicy.apply` guard is `movementType == StockMovementType.PURCHASE_RECEIPT` — **not `isInbound()`**. `resolveCost` untouched. `StockMutationPort` still declares exactly `applyMovement` + `shiftInTransit`. No file under `shared/` added or changed. |
| Unit `*Test` (no Docker) covering R-11, R-18 (HU-INV-03 100@10 + 100@20 → 15 and zero-balance), R-17, R-19, R-16, R-06, R-09, R-13, R-05 | **MET** | `PurchaseOrderStateMachineTest`, `PurchaseReceptionPolicyTest`, `PurchaseOrderBasketPolicyTest`, `UnitConversionPolicyTest`, `PurchaseAccessPolicyTest`, `PurchaseOrderNotesTest`, `WeightedAverageCostPolicyTest`, `ReceivePurchaseServiceTest` — all in surefire (468 green). `WeightedAverageCostPolicyTest.huInv03WorkedExample` asserts `15.0000`; `zeroPriorBalanceYieldsReceivedCost` and a fractional scale-4 case present. |
| A unit test asserting `TRANSFER_IN`, `ADJUSTMENT_POS`, `INITIAL_LOAD` leave `average_cost` untouched (P-06); `add-sales-module` R-21 keeps holding | **MET** | `StockMutationPolicyTest` — "P-06: TRANSFER_IN, ADJUSTMENT_POS and INITIAL_LOAD leave averageCost identical (add-sales-module R-21)" and "the four outbound types leave averageCost identical". `SaleVoidReversalIT` (2) still green in failsafe. |
| `./mvnw verify` green | **MET** | BUILD SUCCESS. |

### PR 2 — infrastructure + web

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| Adapters, controllers, exception handler and `SecurityConfig` matchers for `/api/purchases/**` using `hasAuthority()` | **MET** | `SupplierController` (6 endpoints), `PurchaseOrderController` (7 endpoints), `PurchasesExceptionHandler` (`@RestControllerAdvice(basePackages = "…purchases.infrastructure.adapter.in")` — whole `in` package). `SecurityConfig` lines 132–138 use `hasAuthority`/`hasAnyAuthority`, never `hasRole`, string literals only. |
| `purchases` consumes `StockMutationPort`, implements none of it; never writes `branch_inventories` or `kardex_movements` directly | **MET** | `ReceivePurchaseService` calls `stockMutationPort.applyMovement(...)`. No JPA entity or repository in `purchases` targets `branch_inventories` or `kardex_movements`. |
| `order_number` assigned under the `DT-11`/`DT-12` advisory-lock technique, format `OC-<yyyy>-<nnnn>` (F-9) | **MET** | `PurchaseOrderPersistenceAdapter.create`: `allocateAdvisoryLock("purchase_order_number:" + year)` → `nextSequenceNumber("OC-" + year + "-%")` → `"OC-%d-%04d".formatted(year, sequence)`. `pg_advisory_xact_lock(hashtext(:key))`, transaction-scoped. |
| Every §7 code reachable from at least one controller path — no dead error code | **MET** | See §5 below — all 18 codes mapped in `PurchasesExceptionHandler` and each has a live throw site. |
| `./scripts/validar_esquema.sh` green and **unaffected** | **MET** | 34 checks green; `backend/init-db/` diff empty. |
| `./mvnw verify` green | **MET** | BUILD SUCCESS. |

### PR 3 — verification

| DoD item | Status | Evidence |
| :--- | :--- | :--- |
| `PurchaseReceptionAtomicityIT` — R-15/R-18/R-20/T-01 | **MET** | Present, 2 tests green (incl. forced mid-reception failure / rollback). |
| `PartialReceptionIT` — R-19/HU-COM-04 (100 → 60 → `PARTIALLY_RECEIVED` pending 40; +40 → `RECEIVED`; third reception `invalid_order_state`) | **MET** | Present, 1 test green. |
| `PurchaseOrderStateMachineIT` — R-11/R-12/R-14 (reception refused from `PENDING`/`RECEIVED`/`CANCELLED`; `OPERATOR` refused approval; cancel from `PARTIALLY_RECEIVED` keeps stock, no reversal row) | **MET** | Present, 5 tests green. |
| `PurchaseConcurrencyIT` — R-21/T-02 (two concurrent receptions, no double count, no `500`; two concurrent creations → distinct `order_number`) | **MET** | Present, 2 tests green. |
| `PurchaseBranchIsolationIT` — R-23/R-25/§5 (branch A → `purchase_order_not_found` for branch B; `ADMIN` reads it; corporate `ADMIN` create/receive → `branch_context_required`) | **MET** | Present, 5 tests green. |
| Smoke coverage: supplier CRUD incl. disable/enable, order listing with every filter, cost history (status, envelope shape, no numeric id, `average_cost` never exposed) | **MET** | `PurchasesApiSmokeIT` present, 6 tests green. |
| `docs/deuda_tecnica.md` carries the `DT-13` fiche, registry row, changelog entry (F-9) | **MET** | Registry row at `:55`; fiche at `:411` (Situación actual / Por qué se aceptó / Por qué es deuda / Plan de pago); changelog `v1.7` at `:13`. `validar_trazabilidad.py` item 4 → 13/13 with fiche. |
| `python3 scripts/validar_trazabilidad.py` green — 13 DT with fiche, RF/RNF/RN/CU unchanged | **MET** | `43 RF · 34 RNF · 17 RN · 39 CU · 13 DT`. |
| `./mvnw verify` green, `ModuleBoundariesTest` included | **MET** | BUILD SUCCESS; `ModuleBoundariesTest` ran. |

---

## 4. Behavioural contract — §4, R-00 … R-27 traced to code

Traced against production code, not only tests.

- **R-00 / RNF-PER-04** — `PurchaseOrderController.resolveSize` and `SupplierController.resolveSize` throw `IllegalArgumentException` (→ `400 invalid_request`) when `size < 1 || size > 100`; never clamped (DT-10). No endpoint accepts the acting branch in path/query/body.
- **R-01 / R-03 / R-04** — `ManageSuppliersService` create/edit/disable/enable; `SupplierPersistenceAdapter` uses `saveAndFlush` + `catch (DataIntegrityViolationException)` → `SupplierTaxIdAlreadyExistsException` (`409`). Disable is logical (`is_active=false`), re-enablable; no `DELETE` mapping exists on `SupplierController`. `ManagePurchaseOrdersService` throws `SupplierNotActiveException` (`409`) when a new order names a disabled supplier.
- **R-02 / PA-06** — `SecurityConfig`: `GET /api/purchases/suppliers`, `/api/purchases/suppliers/*` → `authenticated()`; `/api/purchases/suppliers`, `/api/purchases/suppliers/**` → `hasAuthority("ADMIN")`. Read open, write ADMIN-only.
- **R-05 / R-06 / R-08 / R-09** — `PurchaseOrderBasketPolicy` validates the line set (non-empty, no duplicate product → `DuplicateOrderItemException`, `orderedQuantity > 0`, `unitCost >= 0`, discount in range via `DiscountPercent` → `DiscountOutOfRangeException`), converts to base unit via `UnitConversionPolicy` (→ `UnitConversionUnavailableException`), computes each `subtotal` and `totalAmount` server-side; client monetary totals never reach it. Lines returned sorted by product `external_id` (T-02 lock order).
- **R-07 / R-23 / §5\*** — `PurchaseAccessPolicy.resolveActingBranch` throws `BranchContextRequiredException` (→ `403 branch_context_required`) for a corporate `ADMIN`; the acting branch is `AuthenticatedPrincipal.branchId`, never a parameter.
- **R-10 / PA-07** — `PurchaseOrder.withEdit` / `withItems` call `PurchaseOrderStateMachine.require(status, EDIT)` first; `EDIT` is legal only from `PENDING`, else `InvalidOrderStateException` (`409`). Item set replaced atomically via `PurchaseOrderPersistenceAdapter.replaceItems`, `total_amount` recomputed.
- **R-11 / R-12 / R-14** — `PurchaseOrderStateMachine.LEGAL_SOURCES` (a `Map` constant): `PENDING → {EDIT, APPROVE, CANCEL}`, `APPROVED → {RECEIVE, CANCEL}`, `PARTIALLY_RECEIVED → {RECEIVE, CANCEL}`, `RECEIVED → {}`, `CANCELLED → {}`. Exactly five states, both terminals empty, none unreachable. `PurchaseOrderStateMachineTest` (surefire) enumerates state × transition; `PurchaseOrderStateMachineIT` proves reception refused from `PENDING`/`RECEIVED`/`CANCELLED` and `OPERATOR` refused approval (matcher `…/approval` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")`).
- **R-13 / PA-08** — `PurchaseOrder.cancel` requires `require(status, CANCEL)` then `notes.withCancellationReason(reason)`, which throws `CancellationReasonRequiredException` (`400`) on blank. `CANCEL` is legal from `PARTIALLY_RECEIVED`; `TransitionPurchaseOrderService.cancel` performs **no** `applyMovement` — no Kardex row written or deleted. Proven by `PurchaseOrderStateMachineIT`.
- **R-15 / R-20 / T-01** — `ReceivePurchaseService.receive` is `@Transactional`; per plan line it calls `applyMovement(PURCHASE_RECEIPT, effectiveUnitCost, "PURCHASE_ORDER", order.externalId().toString(), …, actor.userId())`, then `save(order.withReception(plan, now))`, then one `audit_logs` entry — all in one transaction. `PurchaseReceptionAtomicityIT` proves stock/avg-cost/Kardex/`received_quantity`/status untouched on forced mid-reception failure.
- **R-16 / PA-02** — `PurchaseReceptionPolicy.plan` computes `excess = max(0, requested − pendingQuantity())`; any `excess > 0` with `actorRole == Role.OPERATOR` → `OverReceiptNotAuthorizedException` (→ `403 over_receipt_requires_manager`). Accepted excess is put into `ReceptionPlan.excesses` and rendered into `payloadAfter` with `authorizingRole` (RNF-OBS-01). Negative quantity → `InvalidOrderQuantityException` (`400`) always.
- **R-17 / PA-09 / D-9** — `PurchaseOrderItem.effectiveUnitCost()` = `unitCost × (1 − discountPercent/100)` at scale 4 `HALF_UP`, defined **only** in the domain record; the cost-history native query returns `unit_cost`/`discount_percent` raw. `PurchaseReceptionPolicy` throws `InvalidUnitCostException` (`400`) when the line has no usable cost — never a default of zero.
- **R-18** — `WeightedAverageCostPolicy.recalculate`: `previousStock.signum() <= 0` → returns `receivedCost`; else `((prev×avg) + (qty×cost)) / (prev+qty)` divided at intermediate scale 8, then `UnitCost` re-rounds to scale 4. `WeightedAverageCostPolicyTest` pins `15.0000` for 100@10 + 100@20 and the zero-balance branch.
- **R-19** — `PurchaseReceptionPolicy` sets `targetStatus = RECEIVED` when every line's accumulated `received >= ordered` after the plan, else `PARTIALLY_RECEIVED`; lines the request does not name keep their stored `received_quantity` and still count. `PurchaseOrder.withReception` accumulates per line and stamps `received_at`. Proven by `PartialReceptionIT`.
- **R-21 / T-02** — `PurchaseOrderSpringDataRepository.findByExternalId` is `@Lock(PESSIMISTIC_WRITE)` with no timeout hint; `ReceivePurchaseService` locks the order row before reading `status`; `PurchaseReceptionPolicy` returns lines sorted by product `external_id`. `PurchaseConcurrencyIT` proves no double count and no `500`.
- **R-22** — `PurchaseReceptionPolicy.plan` throws `IllegalArgumentException` (→ `400 invalid_request`) for an empty or all-zero line set and drops `receivedQuantity == 0` lines from the plan (no Kardex row, no average move). `ReceivePurchaseService` repeats the empty/all-zero guard before any lock.
- **R-23 / R-25 / D-6** — `PurchaseAccessPolicy.assertVisible` returns early for `ADMIN`, else `PurchaseOrderNotFoundException` (`404`, never `403`) when the branch differs. `listingBranchScope` returns `null` for `ADMIN`, the caller's branch otherwise; the order listing and the cost-history query both apply `:branchId IS NULL OR …`. Proven by `PurchaseBranchIsolationIT`.
- **R-24 / R-27** — `PurchaseOrderController.list` paginates and filters by supplier, product, status, date range, sort (`createdAt` | `totalAmount`); no branch parameter. `detail` returns every line with ordered/received/pending, unit cost, discount, effective unit cost, subtotal, plus status/totals/payment terms/cancellation reason.
- **R-26** — `GET /api/purchases/cost-history` → `findCostHistory` native query over `purchase_orders ⋈ purchase_order_items ⋈ suppliers`; **never mentions `branch_inventories`**; returns `effectiveUnitCost` derived in the domain. `PurchasesApiSmokeIT` asserts `average_cost` is never in the payload.

\* §5 note: approval/cancellation act on the stored order's branch, so a corporate `ADMIN` needs no branch context there — `TransitionPurchaseOrderService` does not call `resolveActingBranch`, only `assertVisible`.

---

## 5. Error taxonomy — §7, every code reachable

All 18 codes are mapped in `PurchasesExceptionHandler` (plus `StockMutationRejectedException` reasons) and each has a live throw site:

| Code | HTTP | Throw site |
| :--- | :---: | :--- |
| `invalid_request` | 400 | `IllegalArgumentException` (basket/reception empty or all-zero, `resolveSize` out of range), bean validation, type mismatch |
| `invalid_order_quantity` | 400 | `PurchaseReceptionPolicy` (negative received), `PurchaseQuantity` / basket policy |
| `invalid_unit_cost` | 400 | `PurchaseReceptionPolicy` (no usable cost); `StockMutationRejectedException(UNIT_COST_CONTRACT)` |
| `duplicate_order_item` | 400 | `PurchaseOrderBasketPolicy:66` |
| `discount_out_of_range` | 400 | `DiscountPercent:24` |
| `unit_conversion_unavailable` | 400 | `UnitConversionPolicy:31` |
| `cancellation_reason_required` | 400 | `PurchaseOrderNotes:78` (via `PurchaseOrder.cancel`) |
| `branch_context_required` | 403 | `PurchaseAccessPolicy.resolveActingBranch` (create / receive by corporate `ADMIN`) |
| `over_receipt_requires_manager` | 403 | `PurchaseReceptionPolicy:118` (`OPERATOR` over-receipt) |
| `supplier_not_found` | 404 | `ManageSuppliersService`, `ManagePurchaseOrdersService`, `PurchaseOrderPersistenceAdapter:279` |
| `product_not_found` | 404 | `PurchaseReferenceAdapter:45`; `StockMutationRejectedException(UNKNOWN_PRODUCT)` |
| `purchase_order_not_found` | 404 | `PurchaseAccessPolicy` (wrong branch), `ReceivePurchaseService:93`, lock miss; `StockMutationRejectedException(UNKNOWN_BRANCH)` |
| `purchase_order_item_not_found` | 404 | `PurchaseReceptionPolicy:92`, `ReceivePurchaseService:103` |
| `supplier_tax_id_already_exists` | 409 | `ManageSuppliersService:64`, `SupplierPersistenceAdapter:48/61` |
| `supplier_not_active` | 409 | `ManagePurchaseOrdersService:67/93` |
| `invalid_order_state` | 409 | `PurchaseOrderStateMachine:42`; `StockMutationRejectedException(INSUFFICIENT_STOCK)` defensive |
| `concurrent_order_update` | 409 | `PessimisticLockingFailureException` from the `@Lock` query |
| `duplicate_order_number` | 409 | `PurchaseOrderPersistenceAdapter:81/105` (`DataIntegrityViolationException` on the `UNIQUE`) |

No leaked numeric id, stack trace, SQL text, constraint name, or raw `CANCEL_REASON:` token found in any inspected response record; wrong-branch is `404` never `403`; the cost history never exposes `branch_inventories.average_cost`.

---

## 6. Authorization matrix — §5 vs `SecurityConfig`

`iam/infrastructure/config/SecurityConfig` (lines 125–138), after the `sales` block and before `anyRequest().authenticated()`:

```
1. GET  /api/purchases/suppliers, /api/purchases/suppliers/*        -> authenticated()
2.      /api/purchases/suppliers, /api/purchases/suppliers/**       -> hasAuthority("ADMIN")
3.      /api/purchases/orders/*/approval, /api/purchases/orders/*/cancellation
                                                                    -> hasAnyAuthority("ADMIN","BRANCH_MANAGER")
4.      /api/purchases/**                                           -> authenticated()
```

Order is correct: the `GET` supplier matcher precedes the `ADMIN` supplier matcher (PA-06 — reads open, writes not), and both precede `/api/purchases/**`. All string literals — no `purchases` type imported into `iam` (`ModuleBoundariesTest` green is the backstop). `hasAuthority`/`hasAnyAuthority` only, never `hasRole`, so no `ROLE_` prefix. R-16's over-receipt gate is a domain check in `PurchaseReceptionPolicy`, not a matcher, as designed. Create/edit/receive open to all three roles with branch derived in `PurchaseAccessPolicy`; approve/cancel `ADMIN`+`BRANCH_MANAGER` via matcher 3. Matches §5 exactly.

---

## 7. Invariants that broke this project before

| Invariant | Status | Evidence |
| :--- | :--- | :--- |
| Roles without `ROLE_` prefix, `hasAuthority()` not `hasRole()` | **HELD** | `SecurityConfig` purchases matchers use `hasAuthority("ADMIN")` / `hasAnyAuthority("ADMIN","BRANCH_MANAGER")`. `PurchaseReceptionPolicy` compares `actorRole == Role.OPERATOR`. |
| Stock mutation writes its Kardex row in the same transaction via the synchronous port | **HELD** | `ReceivePurchaseService` is `@Transactional`, single method, per-line `stockMutationPort.applyMovement`; `StockMutationPort` unchanged (joins caller tx, P-01). No `@Async`, no `AFTER_COMMIT` in `purchases` (T-04). |
| RN-10 recalculation fires only on `PURCHASE_RECEIPT`; `average_cost` untouched for every other movement type | **HELD** | `StockMutationPolicy.apply` guard `movementType == StockMovementType.PURCHASE_RECEIPT`; else `resultingAverage = current.averageCost()`. `StockMutationPolicyTest` pins `TRANSFER_IN`/`ADJUSTMENT_POS`/`INITIAL_LOAD` + 4 outbound types unchanged; `SaleVoidReversalIT` still green. |
| Branch derived from the session, never a client parameter | **HELD** | `PurchaseAccessPolicy.resolveActingBranch(actor)`; `applyMovement` uses `order.branchExternalId()`; no command/query record carries a branch id; no endpoint accepts one. |
| API exposes only `external_id` | **HELD** | Controllers type every identifier `UUID` from `externalId()` accessors; JPA adapters are the only classes touching numeric `id` and none returns one; `PurchasesApiSmokeIT` asserts no numeric id in payloads. |
| No class in a direct subpackage of `com.optiplant.inventory` that is not a business module | **HELD** | All new classes under `purchases.*` subpackages or `inventory.domain.service`. `ModuleBoundariesTest` green. |
| `*IT` not `*Test` for Docker tests | **HELD** | All six new Docker tests end in `IT` and run under failsafe; the twelve new unit tests end in `Test` and run under surefire. |

---

## 8. PA / D decisions — spot-checks

| Decision | Status | Evidence |
| :--- | :--- | :--- |
| **PA-01** — WAC recalculation inside `inventory` behind `applyMovement`, no new port, no signature change | **AS WRITTEN** | `WeightedAverageCostPolicy` in `inventory.domain.service`; `StockMutationPort` keeps `applyMovement` + `shiftInTransit`; nothing added under `shared/`. |
| **PA-02** — over-receipt accepted, gated by `BRANCH_MANAGER`/`ADMIN`, audited | **AS WRITTEN** | `PurchaseReceptionPolicy` excess gate + `ReceptionPlan.excesses` + `payloadAfter` `authorizingRole`. |
| **PA-04 / DT-13** — `order_number` `OC-<yyyy>-<nnnn>` via annual advisory lock; `SUBSTRING(order_number FROM 9)` | **AS WRITTEN** | `PurchaseOrderSpringDataRepository.nextSequenceNumber`: `SELECT COALESCE(MAX(CAST(SUBSTRING(order_number FROM 9) AS INTEGER)),0)+1 …` — **offset 9**, not 10. Key literal `purchase_order_number:<yyyy>`. |
| **PA-08** — cancellation from `PARTIALLY_RECEIVED`, no stock reversal | **AS WRITTEN** | `PurchaseOrderStateMachine`: `PARTIALLY_RECEIVED → {RECEIVE, CANCEL}`; `TransitionPurchaseOrderService.cancel` calls no `applyMovement`. Proven by `PurchaseOrderStateMachineIT`. |
| **PA-09** — effective (discount-adjusted) cost feeds RN-10 | **AS WRITTEN** | `ReceivePurchaseService` passes `line.effectiveUnitCost().value()` to `applyMovement(PURCHASE_RECEIPT, …)`, which triggers `WeightedAverageCostPolicy.recalculate` with that cost. |
| **D-6** — cost history branch-scoped by R-25's rule | **AS WRITTEN** | `findCostHistory` has `(:branchId IS NULL OR po.branch_id = :branchId)`; `listingBranchScope` supplies `null` only for `ADMIN`. |
| **D-9** — effective-cost expression only in the domain | **AS WRITTEN** | `PurchaseOrderItem.effectiveUnitCost()` is the sole definition; the native query returns raw `unit_cost` / `discount_percent`. |

---

## 9. Zero schema change

`git diff main...HEAD -- backend/init-db/` is empty. `./scripts/validar_esquema.sh` → 34 checks green, schema intact. Contract §2.5 / design §8 upheld — no `01-init-schema.sql` or `02-seed-data.sql` edit, no `docs/diagrama_er.md` edit.

---

## 10. No foreign files

`git diff --name-status main...HEAD` touches, and only touches:

- `backend/src/main/java/com/optiplant/inventory/purchases/**` (69 new production classes)
- `backend/src/test/java/com/optiplant/inventory/purchases/**` (12 new unit tests + fixtures)
- 3 `inventory` domain files: `WeightedAverageCostPolicy.java` (new), `BranchInventory.java` (M), `StockMutationPolicy.java` (M) — plus their tests `WeightedAverageCostPolicyTest.java` (new), `StockMutationPolicyTest.java` (M)
- `backend/src/test/java/com/optiplant/inventory/{PurchaseReceptionAtomicityIT,PurchaseOrderStateMachineIT,PurchaseConcurrencyIT,PurchaseBranchIsolationIT,PurchasesApiSmokeIT,PartialReceptionIT}.java` (new)
- `backend/src/test/java/com/optiplant/inventory/TestcontainersConfiguration.java` (M)
- `backend/src/main/java/com/optiplant/inventory/iam/infrastructure/config/SecurityConfig.java` (M)
- `openspec/changes/add-purchases-module/**`, `openspec/PLAN.md`, `docs/deuda_tecnica.md`

Nothing under `frontend/`. `SecurityConfig.java` is not in the task's literal enumeration but is explicitly mandated by task 2.7 and §10 PR 2 / design §6.4 — an expected part of the change, not a stray edit.

---

## Issues found

**CRITICAL: none.**

**WARNING: none.**

**SUGGESTIONS (3):**

1. **`PurchaseReceptionPolicy.plan` signature** — design §5 step 5 illustrates `plan(order, lines, actor.role(), now)`; the implementation is `plan(order, commands, actorRole)` with no `now`. The plan carries no timestamp (`received_at` is stamped by `PurchaseOrder.withReception`), so this is a cosmetic divergence from the design's example call with no behavioural impact.
2. **`TestcontainersConfiguration.java` timezone edit** — S2/S3 added `.withEnv("TZ", "America/Bogota")` / `PGTZ` to stabilise pre-existing `sales`/`pricing` ITs near the UTC day boundary. The file is in the permitted-touch list and the change is low-risk and documented in-line, but it is a test-infra fix tangential to `purchases`.
3. **Cost-history `quantity` projection** — `findCostHistory` projects `poi.ordered_quantity AS quantity`. Contract §6 / R-26 name `quantity` without pinning ordered vs received; the ordered quantity is a defensible reading. Noted for the archive reviewer, not blocking.

---

## Final verdict

**PASS** — all 30 task boxes complete and matching code state; all three §10 PR checklists satisfied
in committed code; every `R-00…R-27` traced to a controller/service/domain path; all 18 §7 error
codes reachable; the §5 matrix matches `SecurityConfig`; every project-breaking invariant holds; the
spot-checked PA/D decisions are implemented as written. Three gates green —
`validar_trazabilidad.py` (`43 RF · 34 RNF · 17 RN · 39 CU · 13 DT`, 13/13 fiches),
`validar_esquema.sh` (34 checks, schema unaffected), `./mvnw verify` (BUILD SUCCESS, surefire 468,
**failsafe 199**). Zero schema drift, no foreign files, nothing under `frontend/`. Ready for
`sdd-archive`.
