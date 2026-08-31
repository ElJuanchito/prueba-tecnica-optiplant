# Design — `add-purchases-module`

Step 2 of 3. `contract.md` is authoritative: `R-00…R-27`, `P-01…P-07`, `PA-01…PA-09`, `F-1…F-9`, `T-01…T-07`
and §5 … §9 are settled and are **cited, never restated**. This file decides only what the contract and the
eight shipped modules leave open. **70 new classes** (69 in `purchases`, 1 in `inventory`), **3 modified
production classes**, **1 modified test class**. Zero `backend/init-db/` edits (§2.5) — if a task seems to
need one, §2.5 was wrong: stop and report.

## 1. Placement and graph

Everything new lives under `com.optiplant.inventory.purchases`, except the RN-10 recalculation, which lives
under `com.optiplant.inventory.inventory` (§2). Nothing in a direct subpackage of the base package;
`purchases → shared` only (§2.1); `ModuleBoundariesTest.MODULOS` already lists `purchases` (`:36`) — **no
ArchUnit edit**. `purchases/domain` imports `shared.security.{Role, AuthenticatedPrincipal}` and
`shared.stock.StockMovementType`, nothing else framework-shaped.

## 2. The `inventory` edit — RN-10 behind `StockMutationPort` (P-05/P-06)

The highest-risk edit in this change. Three files, and only three.

### 2.1. Verified starting state

`StockMutationPolicy.apply` (`:38-59`) computes `previousStock`, `resultingStock` and `effectiveCost =
resolveCost(...)`, then returns `current.withStock(...)` — `average_cost` carried through untouched.
`resolveCost` (`:61-73`) already returns the **caller-supplied** cost whenever `requiresSuppliedCost()`,
and `PURCHASE_RECEIPT` is one such type (`StockMovementType:46`), so both RN-10 operands are in hand at
`:41` and `:43`; the `branch_inventories` row is already locked by `StockMutationAdapter.lockOrCreate`
(`:104-107`). And `BranchInventoryPersistenceAdapter.save` already writes
`entity.setAverageCost(inventory.averageCost().value())` (`:88`) — read in the file — so a changed
`averageCost` persists with **no** adapter, mapper, entity or repository change.

### 2.2. New — `inventory/domain/service/WeightedAverageCostPolicy`

Pure Java, no Spring, no Jakarta. The whole of RN-10, and the only place the formula exists:

```java
private static final int INTERMEDIATE_SCALE = 8;

public static UnitCost recalculate(BigDecimal previousStock, UnitCost previousAverage,
        Quantity receivedQuantity, UnitCost receivedCost) {
    if (previousStock.signum() <= 0) {
        return receivedCost;                                        // R-18, zero-balance branch
    }
    return new UnitCost(previousStock.multiply(previousAverage.value())
            .add(receivedQuantity.value().multiply(receivedCost.value()))
            .divide(previousStock.add(receivedQuantity.value()), INTERMEDIATE_SCALE, RoundingMode.HALF_UP));
}
```

`UnitCost`'s compact constructor re-rounds to scale 4 `HALF_UP`, matching `average_cost NUMERIC(14,4)`. **D-2**
— dividing at scale 8 first avoids the double rounding a direct scale-4 division would cause; HU-INV-03's
example is exact either way. Its own class, not a private method (**D-1**): RN-10 is a business rule with a
worked example and deserves its own test target. `CHECK (current_stock >= 0)` makes only `0` reachable on the
guard and `Quantity` guarantees `> 0`, so no division by zero exists.

### 2.3. Modified — `StockMutationPolicy.apply`

Two statements replace one, between the `totalCost` line and the `KardexMovement.Draft`:

```java
UnitCost resultingAverage = movementType == StockMovementType.PURCHASE_RECEIPT
        ? WeightedAverageCostPolicy.recalculate(previousStock, current.averageCost(), quantity, effectiveCost)
        : current.averageCost();
BranchInventory updated = current.withStockAndCost(new StockLevel(resultingStock), resultingAverage, now);
```

The guard is `== StockMovementType.PURCHASE_RECEIPT`, **never `isInbound()`** (P-06): widening it would move
the average on `TRANSFER_IN`, `INITIAL_LOAD` and `ADJUSTMENT_POS`, breaking the archived `add-sales-module`
R-21. `resolveCost` is **not** touched — for `PURCHASE_RECEIPT` it already returns the caller's supplied
cost, which is R-17's effective acquisition cost; for the other seven types `resultingAverage` is the
identity. The Kardex row keeps `effectiveCost` as its `unit_cost`.

### 2.4. Modified — `BranchInventory`, and the tests that pin it

`BranchInventory` gains `withStockAndCost(StockLevel, UnitCost, Instant)`; `withStock` is retained and
delegates to it with `averageCost` unchanged, so the other seven movement types and `StockMovementService`
keep byte-identical behaviour. Its Javadoc (`:11-13`), which says the RN-10 revaluation is "out of scope for
this change", is replaced by a pointer to the new policy. `StockMutationAdapter` is **not** modified: it
already calls `apply`, saves the aggregate and appends the Kardex row in one transaction (PA-01).

**New `WeightedAverageCostPolicyTest`** — R-18: 100 @ 10 receiving 100 @ 20 yields exactly 15; a zero prior
balance yields the received cost; a fractional case pins the scale-4 rounding. **Modified
`StockMutationPolicyTest`** — the P-06 guard as a table: the other three inbound types (`TRANSFER_IN`,
`ADJUSTMENT_POS`, `INITIAL_LOAD`), each with a supplied cost differing from the current average, leave
`averageCost` **identical**, as do the four outbound types. It is what keeps R-21 holding; its Javadoc says so.

## 3.1. `purchases` domain — value objects (`domain/model`)

`SupplierTaxId` (non-blank, ≤ 30 — `VARCHAR(30) NOT NULL`, R-01) · `SupplierName` (non-blank, ≤ 150) ·
`SupplierContact` (`contactName`/`email` ≤ 100, `phone` ≤ 50, `address` ≤ 255; blank → `null`) ·
`OrderNumber` (`OC-\d{4}-\d+`, ≤ 50 — F-9) · `PurchaseQuantity` (`> 0`, scale 4) · `DiscountPercent` (`0 …
100`, scale 2) · `Money` (`>= 0`, scale 4) · `PurchaseOrderNotes` · `PurchasePage<T>`. Each refuses its
matching `CHECK` before the write, so the constraint stays the last defence (T-07).

**D-8** — `PurchaseOrderNotes` is the sole author and reader of the F-3 `CANCEL_REASON:<text>` first-line
token, and `parse` **must not throw** on notes carrying none: it returns `cancellationReason = null` and
the whole text as the human portion. `02-seed-data.sql` seeds no `purchase_orders` row (verified), but
free prose is reachable from `POST /orders`, and this is the leniency `TransferNotes` and `SaleNotes`
already guarantee. The token never leaves the mapper. **D-5** — one `Money` for all three monetary
columns, since two records with identical invariants would be duplication; **D-4** — one generic
`PurchasePage<T>` rather than three page records differing only in element type. Both reverse
mechanically.

### 3.2. Entities and views

```java
record Supplier(UUID externalId, SupplierTaxId taxId, SupplierName name, SupplierContact contact,
        boolean active, Instant createdAt, Instant updatedAt)   // disable() · enable() · withDetails(…)

record PurchaseOrderItem(UUID externalId, UUID productExternalId, PurchaseQuantity orderedQuantity,
        BigDecimal receivedQuantity, Money unitCost, DiscountPercent discountPercent, Money subtotal)

record PurchaseOrder(UUID externalId, OrderNumber orderNumber, UUID branchExternalId,
        UUID supplierExternalId, UUID createdByUserExternalId, PurchaseOrderStatus status,
        String paymentTerms, Money totalAmount, PurchaseOrderNotes notes, Instant receivedAt,
        Instant createdAt, Instant updatedAt, List<PurchaseOrderItem> items)
```

All immutable; every mutation is a `with*` copy advancing `updatedAt` (the schema has no triggers).
`PurchaseOrderItem` exposes `effectiveUnitCost()` = `unitCost × (1 − discount/100)` at scale 4 `HALF_UP`
and `pendingQuantity()` = `max(0, ordered − received)`. **D-9** — that expression lives **only** here,
never in SQL, so R-17 has one authority shared by the reception (§5) and the cost history (§6.3).
`PurchaseOrder` carries `approve()`, `cancel(reason, at)` and `withReception(plan, at)`; no setter
anywhere. `BranchRef`, `SupplierRef`, `UserRef`, `PurchaseOrderDetail`, `PurchaseOrderSummary` and
`CostHistoryEntry` are §6's read-side records, declared locally — importing `sales`' would violate
boundary rule 3.

### 3.3. The state machine (`domain/service/PurchaseOrderStateMachine`)

Mirrors `TransferStateMachine` — a `Map` constant a test enumerates exhaustively (RNF-MAN-01), never a
chain of `if`s, and the only authority on R-10, R-11 and R-14:

```
PENDING            -> { EDIT, APPROVE, CANCEL }        RECEIVED  -> { }   // terminal
APPROVED           -> { RECEIVE, CANCEL }              CANCELLED -> { }   // terminal
PARTIALLY_RECEIVED -> { RECEIVE, CANCEL }
```

`require(current, transition)` throws `InvalidOrderStateException` → `409 invalid_order_state`. The
pessimistic lock brackets the check: the service locks the `purchase_orders` row (`SELECT … FOR UPDATE`,
F-5) **before** reading `status`, so the state a transition validates against is the state it commits
against. Always lock → visibility → state machine → work.

**D-3** — `PurchaseOrderTransition` is `EDIT | APPROVE | CANCEL | RECEIVE`, four values, not five. Whether
a reception lands on `PARTIALLY_RECEIVED` or `RECEIVED` is unknowable before the plan is computed, so
splitting `RECEIVE` would force the caller to pick the target state before the policy that owns R-19 has
run; the target comes back from `PurchaseReceptionPolicy`. Including `EDIT` puts R-10's refusal in the
same table as R-11's. Reversal: split `RECEIVE`, mechanical.

### 3.4. Domain services

**`PurchaseOrderBasketPolicy`** (R-05, R-06, R-08, R-09) — validates the requested line set: non-empty, no
duplicated product, `orderedQuantity > 0`, `unitCost >= 0`, discount in range; converts every quantity to
the base unit; computes each `subtotal = quantity × unitCost × (1 − discount/100)` and `totalAmount` as
their sum (R-06 — client totals never reach it). Returns the lines **already sorted ascending by product
`external_id`**, so the service cannot get the T-02 order wrong. Serves creation and the R-10 edit alike.
**`UnitConversionPolicy`** (R-09, RN-13) — `null` unit ⇒ already base; otherwise multiply by
`conversion_factor` at scale 4; absent or non-positive factor ⇒ `UnitConversionUnavailableException`.
`purchases` declares its own; importing `sales`' would be a `purchases → sales` edge.

**`PurchaseReceptionPolicy`** (R-14, R-16, R-17, R-19, R-22) — the reception's whole rule set:

- refuses a negative quantity (`InvalidOrderQuantityException`) and an all-zero line set
  (`IllegalArgumentException` → `400 invalid_request`, R-22 — `kardex_movements` has `CHECK (quantity >
  0)`, refused before the write per T-07); drops `receivedQuantity == 0` lines from the plan: no Kardex
  row, no move of the average (R-22);
- per line `effectiveUnitCost = item.effectiveUnitCost()`; an absent or unusable cost is
  `InvalidUnitCostException`, **never a default of zero** (R-17, CU-COM-04 EX-02);
- `excess = max(0, received − item.pendingQuantity())`; any `excess > 0` with `actorRole == OPERATOR` ⇒
  `OverReceiptNotAuthorizedException` → `403 over_receipt_requires_manager` (R-16, PA-02). It takes `Role`
  — a `shared` type — not a boolean, so the authorising role reaches the audit payload;
- `targetStatus = RECEIVED` when **every** line's accumulated `received >= ordered` after the plan,
  `PARTIALLY_RECEIVED` otherwise (R-19); lines the request does not name keep their stored
  `received_quantity` and count toward that test. Returns `ReceptionPlan(List<ReceptionLine> lines,
  PurchaseOrderStatus targetStatus, Map<UUID, BigDecimal> excesses)`, **lines sorted ascending by product
  `external_id`** (T-02).

**`PurchaseAccessPolicy`** (R-07, R-23, R-25, §5) — two ordered questions, and the order is the security
property, as in `TransferAccessPolicy`: **(1)** a corporate `ADMIN` creating an order or receiving goods
has no branch to derive ⇒ `BranchContextRequiredException` → `403 branch_context_required`; **(2)**
visibility — `ADMIN` sees every branch, anyone else only their own; another branch's order ⇒
`PurchaseOrderNotFoundException` → `404`, **never `403`**.

### 3.5. Exceptions (`domain/exception`) — 16, one per §7 code bean validation cannot raise

`Supplier{NotFound,NotActive,TaxIdAlreadyExists}Exception`, `PurchaseOrder{NotFound,
ItemNotFound}Exception`, `InvalidOrder{State,Quantity}Exception`, `InvalidUnitCostException`,
`DuplicateOrderItemException`, `DiscountOutOfRangeException`, `UnitConversionUnavailableException`,
`CancellationReasonRequiredException`, `BranchContextRequiredException`,
`OverReceiptNotAuthorizedException`, `ProductNotFoundException`, `DuplicateOrderNumberException`. The last
three repeat names other modules declare — each module declares its own, the precedent `inventory`,
`transfers` and `sales` all follow.

**No domain event.** `purchases` raises no alert (§1), so no `AFTER_COMMIT` listener exists (T-04).

## 4. Use cases and ports

**Primary (`application/port/in`)** — five, one per CU. `ManageSuppliersUseCase`
(`list·get·create·edit·disable·enable`, CU-COM-01) · `ManagePurchaseOrdersUseCase` (`create·edit`,
CU-COM-02) · `TransitionPurchaseOrderUseCase` (`approve·cancel`, CU-COM-03) · `ReceivePurchaseUseCase`
(`receive`, CU-COM-04) · `QueryPurchasesUseCase` (`list·detail·costHistory`, CU-COM-05). Every mutation
takes `AuthenticatedPrincipal actor` — it writes an audit row and derives the branch (RN-14); reads take
it too, since the branch scope is the caller's (R-25). **No command carries a branch id.**

**Secondary (`application/port/out`)** — three, named for the need. `SupplierRepositoryPort`:
`findByExternalId`, `existsByTaxId(taxId, excludingExternalId)`, `create`, `save`, `list(SupplierFilter)`.
`PurchaseOrderRepositoryPort`: `create(NewPurchaseOrder)`, `lockForUpdate(externalId)` (the `SELECT … FOR
UPDATE` of F-5/T-02), `findDetailByExternalId`, `save`, `replaceItems(order, lines)` (R-10),
`list(PurchaseOrderFilter)`, `costHistory(CostHistoryFilter)`. `PurchaseReferencePort`: batch `external_id
→ descriptor` resolution for products, branches, users and suppliers plus `conversionFactors(...)` — one
call per request, never one per line (RNF-PER-01/02). `StockMutationPort`, `AuditWritePort`,
`AuthenticatedPrincipal` and `Role` come from `shared` unchanged; **no new `shared` type** (PA-01).

## 5. Reception — the flow, step by step (CU-COM-04)

One `@Transactional` method on `ReceivePurchaseService`. No Mermaid: an unrendered diagram is an unverified
assertion, and this sequence is linear.

1. `PurchaseAccessPolicy.requireReceivingBranch(actor)` — corporate `ADMIN` ⇒ `403
   branch_context_required` (§5); then an empty or all-zero line set ⇒ `400 invalid_request` (R-22),
   **before any lock is taken**.
2. `purchaseOrderRepository.lockForUpdate(orderExternalId)` — `@Lock(PESSIMISTIC_WRITE)`; empty ⇒
   `PurchaseOrderNotFoundException` (F-5, T-02).
3. `PurchaseAccessPolicy.requireVisible(order, actor)` — another branch's order ⇒ `404` (R-23/R-25); then
   `PurchaseOrderStateMachine.require(order.status(), RECEIVE)` (R-14).
4. Resolve each `itemExternalId` against **the order's own items** — an item outside it ⇒
   `PurchaseOrderItemNotFoundException` (`404`); batch-resolve conversion factors for any supplied unit
   (R-09), one query.
5. `PurchaseReceptionPolicy.plan(order, lines, actor.role(), now)` → `ReceptionPlan` (§3.4), lines already
   in the T-02 lock order.
6. **Per plan line, in that order**, `applyMovement(new StockMutationCommand(order.branchExternalId(),
   productExternalId, PURCHASE_RECEIPT, receivedQuantity, effectiveUnitCost, "PURCHASE_ORDER",
   order.externalId().toString(), notes, actor.userId()))` — balance, average cost and Kardex row move
   together in this same transaction (R-15, P-01, §2).
7. `purchaseOrderRepository.save(order.withReception(plan, now))` — accumulated `received_quantity`,
   `status = plan.targetStatus()`, `received_at = now` (R-19, F-6/PA-05).
8. `auditWritePort.record(...)` — action `RECEIVE_PURCHASE_ORDER`, entity `PURCHASE_ORDER`, `branchId =
   order.branchExternalId()` (T-03), `payloadAfter` naming each received quantity and, when non-zero, its
   accepted excess and the authorising role (R-16, RNF-OBS-01).

R-20 follows by construction: every write is in one transaction and nothing is deferred; steps 1–5 take no
write lock beyond the order row. **`StockMutationRejectedException` mapping**, in
`PurchasesExceptionHandler`: `UNIT_COST_CONTRACT` → `400 invalid_unit_cost`; `UNKNOWN_PRODUCT` → `404
product_not_found`; `UNKNOWN_BRANCH` → `404 purchase_order_not_found` (unreachable through the order's
branch FK, and it must not leak that a branch was involved); `INSUFFICIENT_STOCK` is unreachable from an
inbound movement (P-04) and is mapped defensively to `409 invalid_order_state`.

## 6.1. Adapters — persistence

`SupplierJpaEntity` over `suppliers`; `PurchaseOrderJpaEntity` over `purchase_orders` with
`@OneToMany(cascade = ALL, orphanRemoval = true)` to `PurchaseOrderItemJpaEntity` — the
`transfers`/`sales` shape, which makes R-10's item replacement a list swap. Foreign keys are **plain
`Long` columns, never `@ManyToOne`** to `products`, `branches`, `users` or `suppliers`: an association
would drag other modules' tables into every fetch plan and let a numeric id escape through a getter chain.
No `@Version` (F-5); `updated_at` is application-maintained.

`PurchaseOrderSpringDataRepository.findByExternalId` carries `@Lock(LockModeType.PESSIMISTIC_WRITE)` with
**no `@QueryHints` lock timeout** — verified against `hibernate-core`'s `PostgreSQLDialect`, which renders
only `for update` / `for update nowait` / `for update skip locked`. A lock failure surfaces as
`PessimisticLockingFailureException` → `409 concurrent_order_update` (T-02); a second, unlocked
`findDetailByExternalId` serves the reads (T-05). `SupplierPersistenceAdapter.create`/`save` use
`saveAndFlush` inside `try/catch (DataIntegrityViolationException)` rethrowing
`SupplierTaxIdAlreadyExistsException` — the flush matters: without it the violation surfaces at commit,
outside the adapter, and reaches the client as a `500`; the message names neither the constraint nor the
value. The adapters are the only classes that see a numeric id and none returns one.

### 6.2. `order_number` allocation (F-9, PA-04, DT-13)

The `DT-11`/`DT-12` technique verbatim, inside `PurchaseOrderPersistenceAdapter.create`, before the insert:

```java
int year = Year.now().getValue();
orderRepository.allocateAdvisoryLock("purchase_order_number:" + year);  // pg_advisory_xact_lock(hashtext(:key))
int sequence = orderRepository.nextSequenceNumber("OC-" + year + "-%");
String orderNumber = "OC-%d-%04d".formatted(year, sequence);
```

Lock key literal **`purchase_order_number:<yyyy>`** — distinct from `sale_invoice_number:<yyyy>` and
`transfer_number:<yyyy>`, so the three allocators never contend. `nextSequenceNumber` is `SELECT
COALESCE(MAX(CAST(SUBSTRING(order_number FROM 9) AS INTEGER)), 0) + 1 FROM purchase_orders WHERE
order_number LIKE :pattern` — **offset 9**, not 10: `OC-` is one character shorter than `VEN-`. The
advisory lock is transaction-scoped, so it releases on commit or rollback with no unlock call.
`order_number UNIQUE` stays the last defence → `409 duplicate_order_number` (T-07).

### 6.3. The cost-history query (R-26)

Native, in `PurchaseOrderSpringDataRepository`, over `purchase_orders po JOIN purchase_order_items poi ON
poi.purchase_order_id = po.id JOIN suppliers s ON s.id = po.supplier_id`, with `poi.product_id =
:productId`, `(:supplierId IS NULL OR po.supplier_id = :supplierId)`, `(:branchId IS NULL OR po.branch_id
= :branchId)`, the `po.created_at` range, ordered `po.created_at DESC`, plus its `countQuery`. It **never
mentions `branch_inventories`** — the contract's hardest read-side constraint; the smoke test asserts no
`averageCost` in the payload. The projection returns `unit_cost` and `discount_percent` **raw**;
`CostHistoryEntry` derives `effectiveUnitCost` through `PurchaseOrderItem.effectiveUnitCost()` (D-9).

**D-6** — the history is branch-scoped by the order listing's rule: `ADMIN` network-wide, everyone else
their own branch. R-26 names no branch rule, but the rows it returns are order lines and R-25 forbids
showing another branch's orders; a history leaking them through a different endpoint would be R-25 with an
open back door. Reversal: one predicate.

**Index note, not new debt.** `purchase_order_items` has no index on `product_id` (`:299` declares only
`idx_purchase_items_order`), so the query is driven from `purchase_orders` — `idx_purchases_branch`, or
`idx_purchases_supplier` when that filter is present — and the product predicate applies over the joined
rows through `idx_purchase_items_order`, the access path §9 names. Per-branch order volume is bounded, so
RNF-PER-01 holds without a schema change (§2.5). **No `DT` fiche is filed**: §10 PR3 pins the count at
13/13 and a fourteenth would fail `validar_trazabilidad.py`; the fix, if ever needed, is one `CREATE INDEX`.

### 6.4. Web and security

`SupplierController` at `/api/purchases/suppliers` (six endpoints) and `PurchaseOrderController` at
`/api/purchases` (`/orders`, `/orders/{id}`, `/orders/{id}/approval`, `/orders/{id}/cancellation`,
`/orders/{id}/receptions`, `/cost-history`) — §6 verbatim, request/response records nested in the
controller as `SaleController` does. Page size: a private `resolveSize` per controller, default 20, max
100, **out of range throws** → `400 invalid_request` (R-00, DT-10 — never clamped). No `DELETE` mapping
exists (R-03, RN-12). `PurchasesExceptionHandler` is `@RestControllerAdvice(basePackages =
"…purchases.infrastructure.adapter.in")` — **the whole `in` package**, not `in.web`.

**`SecurityConfig` — ordering is the whole trap.** Four matchers, after the `sales` block and **before**
the final `authenticated()` catch-all, string literals only (importing a `purchases` type into `iam` would
create `iam → purchases`), `hasAuthority` never `hasRole`:

```java
.requestMatchers(HttpMethod.GET, "/api/purchases/suppliers", "/api/purchases/suppliers/*").authenticated()
.requestMatchers("/api/purchases/suppliers", "/api/purchases/suppliers/**").hasAuthority("ADMIN")
.requestMatchers("/api/purchases/orders/*/approval", "/api/purchases/orders/*/cancellation")
        .hasAnyAuthority("ADMIN", "BRANCH_MANAGER")
.requestMatchers("/api/purchases/**").authenticated()
```

The `GET` matcher must precede the `ADMIN` supplier matcher (PA-06: reads open, writes not), and both must
precede `/api/purchases/**`, or they are dead. **R-16's over-receipt gate is not a matcher** — it depends
on the body against the stored pending balance, so it stays a domain check.

## 7. Transaction boundaries

| Operation | One transaction (T-01) | Locks (T-02) |
| :--- | :--- | :--- |
| Supplier create / edit / disable / enable | supplier row + audit, `branch_id = NULL` (T-03) | none |
| Create order (R-05) | advisory lock + `purchase_orders` insert + items insert + audit; **no balance** | advisory `purchase_order_number:<yyyy>` |
| Edit order (R-10) | order update + full item replacement + recomputed `total_amount` + audit | `FOR UPDATE` on the order row |
| Approve (R-12) | status update + audit | `FOR UPDATE` on the order row |
| Cancel (R-13) | status + `CANCEL_REASON` token in `notes` + audit; **no Kardex row written or deleted** (RN-12, PA-08) | `FOR UPDATE` on the order row |
| Reception (R-15/R-20) | per line `applyMovement` (balance + `average_cost` + Kardex) + `received_quantity` + status + `received_at` + audit | order row first, then `branch_inventories` by product `external_id` ascending |
| Every read (list, detail, cost history, suppliers) | `@Transactional(readOnly = true)` | none (T-05, RN-09) |

All isolation is READ COMMITTED. `StockMutationPort` calls join the caller's transaction
(`Propagation.REQUIRED`, P-01) — never `REQUIRES_NEW`, never `@Async`. **Nothing in this module is
`AFTER_COMMIT`** (T-04), so no listener exists to fail. **Lock order:** a reception touches only its own
branch's rows, so ordering by product `external_id` ascending suffices to make two concurrent receptions
over overlapping products serialize rather than deadlock (T-02); `PurchaseReceptionPolicy` returns the
plan already sorted — the construction `TransferDispatchPolicy` uses — and the order row is always locked
first. `audit_logs.branch_id` is the branch of the **mutated resource** (T-03): the order's branch for
create, edit, approve, cancel and receive, `null` for every supplier action. `audit_logs.action` has no
`CHECK`, so the strings are `CREATE_PURCHASE_ORDER`, `UPDATE_PURCHASE_ORDER`, `APPROVE_PURCHASE_ORDER`,
`CANCEL_PURCHASE_ORDER`, `RECEIVE_PURCHASE_ORDER`, and `AuditAction.CREATE/UPDATE/DISABLE/ENABLE` for
suppliers.

## 8. Persistence and technical debt — no change

`01-init-schema.sql` and `02-seed-data.sql` are **not** edited (§2.5), so `docs/diagrama_er.md` needs no
edit and `./scripts/validar_esquema.sh` must stay green **and unaffected**; if it must change, §2.5 was
wrong: stop and report. **No new debt is filed**: `DT-13` is already registered in `docs/deuda_tecnica.md`
(`:55` row, `:411` fiche) by the contract phase — S3 verifies it, it does not write it — and §6.3's index
observation is deliberately not a fiche, so `validar_trazabilidad.py` keeps reporting **13 DT declared, 13
with fiche**.

## 9. Decisions taken here, and their reversal cost

| # | Decision | Reversal |
| :--- | :--- | :--- |
| D-1 | RN-10's arithmetic is a new `WeightedAverageCostPolicy`, called from `StockMutationPolicy.apply` under a `PURCHASE_RECEIPT` guard (§2.2) | inline it, delete one class |
| D-2 | Divide at intermediate scale 8 before `UnitCost` rounds to 4 (§2.2) | one constant |
| D-3 | `PurchaseOrderTransition` is `EDIT \| APPROVE \| CANCEL \| RECEIVE`; the partial/total target comes from `PurchaseReceptionPolicy` (§3.3) | split `RECEIVE` in two |
| D-4 | One generic `PurchasePage<T>` instead of three page records (§3) | split into three |
| D-5 | One `Money` record for `unit_cost`, `subtotal` and `total_amount` (§3) | introduce a distinct cost record |
| D-6 | The cost history is branch-scoped by R-25's rule (§6.3) | drop one predicate |
| D-7 | Reception lines are addressed by `itemExternalId` (§6) but sorted by the item's **product** `external_id` for the T-02 lock order (§3.4/§5) | none — both halves are contract-fixed |
| D-8 | `PurchaseOrderNotes.parse` never throws on notes carrying no token (§3) | none |
| D-9 | The effective-cost expression lives only in `PurchaseOrderItem`, never in SQL (§3.2, §6.3) | a SQL expression, at the cost of a second definition |

**Rejected.** A `purchase_order_receipts` table (PA-05 — a migration §2.5 forbids). A new `shared`
valuation-write port (PA-01 — a second write and a second lock on a row `applyMovement` already holds).
Triggering the recalculation from `isInbound()` (P-06). A trigger or stored procedure for RN-10
(CLAUDE.md: constraints are the last defence, never the first). Flyway alongside `init-db/` (DT-01).
Importing `sales`' `Money` or `UnitConversionPolicy` (boundary rule 3). `@ManyToOne` on the JPA entities.
Optimistic locking on `purchase_orders` (F-5). Clamping an oversized page (DT-10). Reversing received
stock on cancellation from `PARTIALLY_RECEIVED` (PA-08).

## 10. Traps specific to this change

1. **`SUBSTRING` offset is 9, not 10** (§6.2). Copying `SaleSpringDataRepository`'s query gives
   `OC-2026-0001 → 026-0001` and a runtime cast failure, not a compile error.
2. **The WAC guard is `== PURCHASE_RECEIPT`, never `isInbound()`** (§2.3, P-06). `SaleVoidReversalIT`
   asserts `average_cost` is unmoved by an `ADJUSTMENT_POS`; widening the guard turns that green test red
   and it will look like a `sales` regression. `resolveCost` must not be touched for the same reason.
3. **The order row is locked before `status` is read** (§3.3). A state check on an unlocked read is R-21's
   exact failure: two concurrent receptions both see `APPROVED` and both apply.
4. **Ship the S1 services unannotated** while their out-ports have no adapter — S2 restores `@Service`;
   registering them in S1 breaks `ApplicationContextIT`, as in `add-inventory-module`.
5. **Security matcher order** (§6.4), and **every §7 code reachable from a controller path** —
   `concurrent_order_update` and `duplicate_order_number` only under contention, but still mapped.
