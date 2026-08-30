# Tasks: `add-purchases-module`

Three slices, one PR each, matching `contract.md` §10 and `openspec/PLAN.md` §3; `design.md` is cited by
section. **Zero schema change**: if a task seems to need one, §2.5 was wrong — stop and report. The
`JpaEntity → Mapper → SpringDataRepository → PersistenceAdapter → Controller → ExceptionHandler` chain
already exists in `inventory`, `transfers` and `sales`: replicate it, never invent it.

**The RN-10 edit inside `inventory` is in S1, not S2.** It is pure domain — no Spring, no Jakarta, no
adapter change, since `BranchInventoryPersistenceAdapter.save:88` already persists `average_cost` — and
§10 PR 1 makes it and its two unit tests part of PR 1's own definition of done.

## Phase 1 — S1: the RN-10 edit, `purchases` domain and application (PR1)

- [x] 1.1 Create `inventory/domain/service/WeightedAverageCostPolicy` (design §2.2, RN-10/R-18) — pure
      static `recalculate(previousStock, previousAverage, receivedQuantity, receivedCost)`, zero-balance
      branch returning the received cost, division at `INTERMEDIATE_SCALE = 8` before `UnitCost` rounds to 4.
- [x] 1.2 Edit `inventory/domain/model/BranchInventory` — add `withStockAndCost(StockLevel, UnitCost,
      Instant)`, make `withStock` delegate to it unchanged, and replace its Javadoc's "out of scope for
      this change" sentence with a pointer to 1.1 (design §2.4).
- [x] 1.3 Edit `inventory/domain/service/StockMutationPolicy.apply` — the two statements of design §2.3,
      guarded by `movementType == StockMovementType.PURCHASE_RECEIPT` (**P-06, never `isInbound()`**).
      **Do not touch `resolveCost`.** No adapter, mapper, entity or repository edit is needed or allowed.
- [x] 1.4 Unit `WeightedAverageCostPolicyTest` (R-18) — the HU-INV-03 worked example (100 @ 10 + 100 @ 20
      ⇒ exactly 15), the zero-prior-balance case, and a fractional case pinning the scale-4 rounding.
- [x] 1.5 Extend `inventory/.../StockMutationPolicyTest` (P-06) — `TRANSFER_IN`, `ADJUSTMENT_POS` and
      `INITIAL_LOAD` with a supplied cost differing from the current average leave `averageCost`
      **identical**, and so do the four outbound types. Javadoc names `add-sales-module` R-21.
- [x] 1.6 Create `purchases/domain/model/` value objects (design §3.1): `SupplierTaxId`, `SupplierName`,
      `SupplierContact`, `OrderNumber`, `PurchaseQuantity`, `DiscountPercent`, `Money`, `PurchasePage<T>`,
      and `PurchaseOrderNotes` — the **only** reader and writer of the F-3 `CANCEL_REASON:` token, whose
      `parse` must not throw on free prose (D-8).
- [x] 1.7 Create `purchases/domain/model/` entities and views (design §3.2): `Supplier`,
      `PurchaseOrderItem` (with `effectiveUnitCost()` per R-17/D-9 and `pendingQuantity()`),
      `PurchaseOrder` (`approve`, `cancel`, `withReception`), `PurchaseOrderStatus`,
      `PurchaseOrderTransition`, `BranchRef`, `SupplierRef`, `UserRef`, `PurchaseOrderDetail`,
      `PurchaseOrderSummary` and `CostHistoryEntry` — all immutable, no setters.
- [x] 1.8 Create `purchases/domain/service/` (design §3.3, §3.4): `PurchaseOrderStateMachine` (R-10/R-11/
      R-14 as a `Map` constant), `PurchaseOrderBasketPolicy` (R-05/R-06/R-08/R-09, lines returned **in the
      T-02 lock order**), `UnitConversionPolicy` (R-09/RN-13), `PurchaseReceptionPolicy` (R-14/R-16/R-17/
      R-19/R-22, plan sorted by product `external_id`), `PurchaseAccessPolicy` (R-07/R-23/R-25 — visibility
      before side, `404` never `403`); plus the sixteen exception types of §3.5.
- [x] 1.9 Create `purchases/application/port/out/`: `SupplierRepositoryPort`,
      `PurchaseOrderRepositoryPort`, `PurchaseReferencePort` (design §4). No new `shared` type (PA-01).
- [x] 1.10 Create `purchases/application/port/in/` and `service/` (design §4, §5): the five use cases and
      their five services plus `PurchaseOrderDetailAssembler`. Reception order is exactly design §5's
      steps 1–8; every mutation writes its `audit_logs` entry in the same transaction with `branch_id` =
      the order's branch, `null` for suppliers (T-01, T-03).
- [x] 1.11 Unit `PurchaseOrderStateMachineTest` (R-11 — every state × transition, both terminals, plus
      R-10's `EDIT` refusal) and `PurchaseReceptionPolicyTest` (R-16 over-receipt gating for all three
      roles, R-17 effective cost, R-19 partial vs total, R-22 zero and all-zero lines).
- [x] 1.12 Unit `PurchaseOrderBasketPolicyTest` (R-06 totals, R-08 duplicates, lock ordering),
      `UnitConversionPolicyTest` (R-09 incl. the unconvertible unit), `PurchaseAccessPolicyTest`
      (R-07/R-23/R-25: other branch ⇒ *not found*, corporate `ADMIN` ⇒ `branch_context_required`),
      `PurchaseOrderNotesTest` (F-3 round trip, missing token, token absent from the exposed note).
- [x] 1.13 Unit service tests with stubbed ports — `applyMovement` called once per non-zero line **in
      product order** with `PURCHASE_RECEIPT`, the effective cost, `reference_type = "PURCHASE_ORDER"`,
      `reference_id` = the order's `external_id` (R-15), plus an audit entry per mutation (T-01/T-03).
- [x] 1.14 Verify `rg "org\.springframework|jakarta\.persistence" purchases/domain` returns nothing, then
      `cd backend && ./mvnw verify` (`ModuleBoundariesTest`, `SharedIsFrameworkFreeTest` included). **Ship
      the five application services unannotated** while their out-ports have no adapter — 2.6 restores
      `@Service`; registering them now breaks `ApplicationContextIT` (design §10 trap 4).

## Phase 2 — S2: infrastructure and web (PR2)

- [ ] 2.1 Create `.../out/persistence/supplier/`: `SupplierJpaEntity`, `SupplierMapper`,
      `SupplierSpringDataRepository` (paged native search over `name`/`tax_id` plus `is_active`, with its
      `countQuery`), `SupplierPersistenceAdapter` (`saveAndFlush` + `DataIntegrityViolationException` →
      `SupplierTaxIdAlreadyExistsException`, R-01; the flush is not optional — design §6.1).
- [ ] 2.2 Create `PurchaseOrderJpaEntity` + `PurchaseOrderItemJpaEntity` (`@OneToMany(cascade = ALL,
      orphanRemoval = true)`), `PurchaseOrderMapper` — the only place the F-3 token is written or read —
      and `PurchaseOrderSpringDataRepository` with `findByExternalId` annotated `@Lock(PESSIMISTIC_WRITE)`,
      **no `@QueryHints` timeout** (design §6.1, F-5). FKs are plain `Long`, never `@ManyToOne`.
- [ ] 2.3 Add to `PurchaseOrderSpringDataRepository` the `allocateAdvisoryLock` / `nextSequenceNumber`
      pair of design §6.2 — key `purchase_order_number:<yyyy>`, `SUBSTRING(order_number FROM 9)`
      (**offset 9, not 10** — design §10 trap 1) — plus the paged order listing with every §6 filter and
      the cost-history query of §6.3, which **never mentions `branch_inventories`**.
- [ ] 2.4 Create `PurchaseOrderPersistenceAdapter` — `OC-<yyyy>-<nnnn>` allocation (F-9/DT-13), branch-
      scoped detail and listing (R-25), `replaceItems` for the R-10 edit, `lockForUpdate` mapping
      `PessimisticLockingFailureException` → `concurrent_order_update` and the `order_number UNIQUE`
      violation → `duplicate_order_number` (T-07).
- [ ] 2.5 Create `PurchaseReferenceSpringDataRepository` and `PurchaseReferenceAdapter` (design §4) —
      native `external_id → id` resolution for products, branches, users and suppliers, batched
      descriptors for the read side, and `conversion_factor` lookup. One query per request, no N+1.
- [ ] 2.6 Create `SupplierController` and `PurchaseOrderController` (contract §6's fourteen operations —
      no branch anywhere (RN-14), oversized page **rejected** not clamped (R-00), no numeric id, no raw
      `CANCEL_REASON` token, no `DELETE` mapping) and `PurchasesExceptionHandler` scoped to
      `…purchases.infrastructure.adapter.in` — **the whole `in` package** (design §6.4) — mapping every §7
      code plus design §5's `StockMutationRejectedException` reasons. Restore `@Service` on the S1 services.
- [ ] 2.7 Edit `iam/.../config/SecurityConfig` with design §6.4's four matchers **in that order** — `GET`
      suppliers before the `ADMIN` supplier rule, both before `/api/purchases/**` — **string literals
      only**, `hasAuthority` never `hasRole`. R-16's over-receipt gate is **not** a matcher.
- [ ] 2.8 Verify every §7 code is reachable from a controller path (name the path per code in the PR
      description — no dead error code), run `./scripts/validar_esquema.sh` (green **and unaffected**,
      §2.5 — if it must change, stop and report) and `cd backend && ./mvnw verify`.

## Phase 3 — S3: cross-cutting verification and documentation (PR3)

**Docker-needing classes end in `IT`, never `Test`.** The list is fixed by §10 PR 3: add none, drop none.

- [ ] 3.1 `PurchaseReceptionAtomicityIT` — R-15/R-18/R-20/T-01: stock incremented, `average_cost`
      recalculated to the RN-10 value, one `PURCHASE_RECEIPT` Kardex row per line with
      `reference_type = 'PURCHASE_ORDER'` and `reference_id` = the order's `external_id`; a forced
      mid-reception failure leaves order, `received_quantity`, balances, average cost and Kardex untouched.
- [ ] 3.2 `PartialReceptionIT` — R-19/HU-COM-04: 100 ordered, 60 received ⇒ `PARTIALLY_RECEIVED`, pending
      40; the remaining 40 ⇒ `RECEIVED`; a third reception refused with `invalid_order_state`.
- [ ] 3.3 `PurchaseOrderStateMachineIT` — R-11/R-12/R-14: reception refused from `PENDING`, `RECEIVED` and
      `CANCELLED` (RN-15); an `OPERATOR` refused the approval; cancellation from `PARTIALLY_RECEIVED`
      keeps the received stock and writes no reversal row (PA-08, RNF-INT-02).
- [ ] 3.4 `PurchaseConcurrencyIT` — R-21/T-02: two concurrent receptions on one order, no double count, no
      `500`; two simultaneous creations yield two distinct `order_number` values (F-9).
- [ ] 3.5 `PurchaseBranchIsolationIT` — R-23/R-25/§5: branch A gets `purchase_order_not_found` for branch
      B's order, `ADMIN` reads it, a corporate `ADMIN` creating or receiving gets `branch_context_required`.
- [ ] 3.6 `PurchasesApiSmokeIT` — supplier CRUD incl. disable/enable (R-03), order listing with every §6
      filter, and the cost history: status, page-envelope shape, no numeric id, no raw `CANCEL_REASON`
      token, **`average_cost` never exposed** (R-26, RNF-API-02).
- [ ] 3.7 Verify `docs/deuda_tecnica.md` already carries the **`DT-13`** fiche, registry row and changelog
      entry (written in the contract phase — check only, do not write). Update `openspec/PLAN.md` §1–§2 and
      confirm `/v3/api-docs` documents all fourteen operations (RNF-API-01).
- [ ] 3.8 Run `python3 scripts/validar_trazabilidad.py` (green — **13 DT declared, 13 with fiche**, RF/RNF/
      RN/CU unchanged), `./scripts/validar_esquema.sh` (green, unchanged) and `cd backend && ./mvnw verify`.
