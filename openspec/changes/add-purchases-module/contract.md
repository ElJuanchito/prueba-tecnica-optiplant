# Contract — `add-purchases-module`

Acceptance contract for the `purchases` module package.
Step 1 of 3: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §4
(RN-01 … RN-17), `docs/casos_de_uso.md` §2.3, §3.3 and §5 (CU-COM-04), and
`docs/historias_de_usuario.md` HU-COM-01 … HU-COM-04 plus **HU-INV-03** alongside this document.

---

## 1. Scope

One module package: **`purchases`** — suppliers, purchase orders with their five-state machine,
goods reception (total or partial) and the weighted-average-cost recalculation the whole system
depends on for valuation. It is the **third** consumer of `shared/stock/StockMutationPort`
(`transfers` proved it, `sales` reused it) and the **first inbound** one: every other consumer to
date moves stock outward. `docs/decisiones_arquitectura_tecnica.md` §2.4 is unchanged.

**Out of scope:** any schema change (§2.5, zero `backend/init-db/` edits); a reception document
entity (PA-05); supplier performance scoring or rating (no `RF` demands it); purchase returns to
supplier (no `RF`, no Kardex constant, no `HU`); accounts payable, invoices or payment execution —
`payment_terms` is recorded text, never a scheduled obligation (SRS §1.3 excludes general
accounting); tax on a purchase order (no column, no `RF`); purchase dashboards and cost KPIs
(`CU-DSH-01` … `CU-DSH-03`, owned by `add-analytics-module`); resolving or clearing an existing
`STOCK_MINIMUM` alert on receipt (no `RF`/`HU` demands it — `notifications` owns alert lifecycle);
and the frontend.

---

## 2. Affected modules

### 2.1. Dependency directions

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `purchases` | `shared` | `AuthenticatedPrincipal`, `Role`, `StockMutationPort`, `StockMovementType`, `AuditWritePort` | one-way |
| `purchases` → `inventory` | — | `StockMutationPort` on `shared` | no direct edge |
| `purchases` → `catalog` | — | native `external_id → id` resolution in its own persistence adapter | no direct edge |

Every cross-module contact is a `shared` type, so the graph stays acyclic and `shared` keeps
importing no module name. `ModuleBoundariesTest.MODULOS` already lists `purchases`
(`ModuleBoundariesTest.java:36`) — **no ArchUnit change is needed**. Foreign keys to `products`,
`branches` and `users` are resolved by `purchases`' own persistence adapter with native
`external_id → id` queries, exactly as `inventory`, `transfers` and `sales` already do: no lookup
port, no new edge.

### 2.2. Inherited decisions — not reopened

- **P-01/P-02** `StockMutationPort.applyMovement` mutates `current_stock` and inserts the Kardex row in the **caller's** transaction (RN-02, RNF-INT-01). Never `@Async`, never `AFTER_COMMIT`, never two writes. It takes the `branch_inventories` row lock itself.
- **P-03** `PURCHASE_RECEIPT` is inbound, so `StockMovementType.requiresSuppliedCost()` is `true` and the caller MUST supply a unit cost (`StockMovementType.java:46`, `StockMutationPolicy.resolveCost`). An absent cost is a contract violation, not a default of zero — CU-COM-04 EX-02.
- **P-04** `StockMutationRejectedException` reasons are mapped by each consumer to its own status. An inbound receipt cannot underflow, so `INSUFFICIENT_STOCK` is unreachable from this module.
- **DT-10** Page size uses the `inventory`/`transfers`/`sales` **rejection** pattern, never `catalog`'s silent clamp.

### 2.3. Decision — the WAC recalculation lives behind `StockMutationPort`

**P-05** Verified in the code: `StockMutationPolicy.apply` returns `current.withStock(...)` and
**never touches `average_cost`** — no operation in the system recalculates the weighted average
today. `purchases` MUST NOT close that gap by writing `branch_inventories` itself: that table
belongs to `inventory` and the write would create the edge §2.1 forbids, plus a second lock on a row
the port already holds.

The recalculation MUST therefore happen **inside `inventory`, in the same `applyMovement` call**
that produces the balance and the Kardex row. The port needs **no new method and no signature
change**: `StockMutationPolicy` already holds both operands RN-10 requires — `current.averageCost()`
and the supplied `UnitCost`. One write, one lock, one transaction, impossible to desync by
construction. No new `shared` port is introduced (PA-01).

**P-06** The recalculation MUST be triggered by `movementType == PURCHASE_RECEIPT` **exclusively**,
not by `isInbound()`. RN-10 traces only to RF-COM-04, and widening it would silently move the
average on `TRANSFER_IN`, `INITIAL_LOAD` and `ADJUSTMENT_POS` — breaking the already-archived and
already-tested `add-sales-module` R-21 (a sale void must not alter `average_cost`).

### 2.4. Decision — reception is the only writer of received quantities

**P-07** `purchase_order_items.received_quantity` is accumulated **only** by a reception (R-14). No
other module reads or writes `purchase_orders`, `purchase_order_items` or `suppliers`; `purchases`
is their only writer. `analytics` will read them later, read-only.

### 2.5. Schema findings — reported, not migrated

| # | Finding | Resolution inside the existing schema |
| :--- | :--- | :--- |
| **F-1** | `suppliers` has **no commercial-conditions column** (`01-init-schema.sql:250-263`: contact data, `tax_id`, `is_active` only), yet RF-COM-06 names "condiciones comerciales". It has no `notes` column either, so the token technique F-3 uses has no home. | The commercial condition is captured **per order** in `purchase_orders.payment_terms`, which is where RF-COM-01 requires it and where it is contractually binding. The supplier record holds identity and contact data. No unenforced default is invented (PA-03). |
| **F-2** | `purchase_orders` has **no `approved_by`, `approved_at`, `cancelled_at` or `cancellation_reason`**. | Actor and timestamp of every transition live in `audit_logs` (entity `PURCHASE_ORDER`), already exposed by CU-SEG-04 and demanded by RF-VAL-02, which names order cancellations verbatim. `received_at` exists and is stamped on each reception. |
| **F-3** | The mandatory cancellation reason has no column. | A deterministic first-line token in `purchase_orders.notes` (`CANCEL_REASON:<text>`), the technique `transfers` uses for priority and `sales` for its void reason. The API exposes `cancellationReason` and never leaks the token. |
| **F-4** | `purchase_order_items` has **no unit-of-measure column**. | RN-13 settles it: quantities persist in the product's base unit; an alternative unit in the request is converted on entry via `conversion_factor` and never stored. |
| **F-5** | `purchase_orders` has **no version column**, so optimistic locking is unavailable for the transitions. | Pessimistic row lock (`SELECT … FOR UPDATE`) on the `purchase_orders` row before every transition (T-02). |
| **F-6** | There is **no reception table**: a partial reception has nowhere to persist as its own document. | The reception history **is** the Kardex: one `PURCHASE_RECEIPT` row per received line with `reference_type = 'PURCHASE_ORDER'` and `reference_id` = the order's `external_id`, plus the accumulated `received_quantity`. `received_at` holds the most recent reception (PA-05). |
| **F-7** | `received_quantity` carries only `CHECK (>= 0)` — **over-receipt is representable**, unlike `transfers`, where RN-06 made it impossible. | CU-COM-04 FA-02 and HU-COM-04 require exactly that, gated by manager authorization (R-16, PA-02). The schema does not need to forbid what the requirement allows. |
| **F-8** | `purchase_orders` has no `subtotal` or `discount_amount`, only `total_amount`; `purchase_order_items` has `unit_cost`, `discount_percent` and `subtotal`. | Totals are computed by the backend from the lines and never accepted from the client (R-06, RNF-SEC-05). No tax is modelled anywhere on a purchase and none is invented. |
| **F-9** | `order_number` has **no sequence**, only `UNIQUE`. | Assigned the way `transfer_number` (`DT-11`) and `sales.invoice_number` (`DT-12`) already are, with format `OC-<yyyy>-<nnnn>`. Registered as **`DT-13`** in `docs/deuda_tecnica.md` in this same change (PA-04). |

None of these blocks any of the five use cases. **No `backend/init-db/` change is proposed.**

---

## 3. Traceability

Every identifier below was verified present in its source document.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| RF-COM-06 | CU-COM-01 | HU-COM-03 |
| RF-COM-01 | CU-COM-02 | HU-COM-01 |
| RF-COM-05 | CU-COM-03 | HU-COM-01 |
| RF-COM-02, RF-COM-04 | CU-COM-04 | HU-COM-04, **HU-INV-03** *(prioritized in the technical-test statement)* |
| RF-COM-03 | CU-COM-05 | HU-COM-02 |
| RF-INV-05 | CU-COM-04 *(a receipt is an inbound movement)* | HU-INV-03 |
| RF-INV-08 | traced by CU-COM-04 | HU-INV-02 |
| RF-VAL-02 | CU-COM-03 *(order cancellations named verbatim in RF-VAL-02)* | HU-INV-04 |
| RN-02, RN-03, RN-10, RN-12, RN-13, RN-14, RN-15 | constrain the above | — |

**No new `RF` / `RNF` / `RN` is required**, so the traceability matrix of `docs/casos_de_uso.md`
gains no row. The only `docs/` edit in this change is the **`DT-13`** fiche plus its registry row
and changelog entry (F-9). `python3 scripts/validar_trazabilidad.py` — verified green while writing
this contract, **43 RF · 34 RNF · 17 RN · 39 CU · 13 DT** — must still pass unchanged.

---

## 4. Behavioural contract

**R-00** Every collection endpoint MUST paginate with a server-side maximum and MUST **reject** an
oversized page with `400 invalid_request` (`DT-10`). No endpoint accepts the acting branch in path,
query or body (RN-14).

### Manage suppliers (CU-COM-01, RF-COM-06)

- **R-01** A supplier MUST persist with a unique `tax_id`, a name, optional contact data, and `is_active = true`. *Given* a `tax_id` already stored, *then* `409 supplier_tax_id_already_exists`.
- **R-02** Suppliers are **corporate** data, not branch-scoped: `suppliers` has no `branch_id`. Every authenticated user MUST be able to read them — an operator cannot create an order otherwise — while writes are `ADMIN`-only (§5, PA-06).
- **R-03** Disabling MUST be **logical** (`is_active = false`), never a physical delete, preserving the order history (HU-COM-03 second criterion, RN-12's spirit). A disabled supplier MUST be re-enablable.
- **R-04** *Given* a disabled supplier, *when* a new order names it, *then* `409 supplier_not_active`. Orders already referencing it stay readable and receivable.

### Create and edit a purchase order (CU-COM-02, RF-COM-01)

- **R-05** An order MUST persist as `PENDING` with a unique `order_number` (F-9), the session branch, the acting user, an active supplier, optional `payment_terms`, and at least one item with `ordered_quantity > 0` in the base unit (RN-13), a `unit_cost >= 0` and a `discount_percent` within `0 … 100` (HU-COM-01 first criterion).
- **R-06** The backend MUST compute each `subtotal` and the order's `total_amount` from quantity, unit cost and discount (HU-COM-01 fourth criterion). *Given* monetary totals in the request, *then* they are ignored, never trusted (F-8, RNF-SEC-05).
- **R-07** The acting branch MUST be `AuthenticatedPrincipal.branchId` (RN-14). *Given* a corporate `ADMIN` (`branchId == null`), *then* `403 branch_context_required` — there is no branch to derive.
- **R-08** *Given* an unknown, disabled or duplicated product, *then* the order is refused with nothing written; *given* zero items, *then* `400 invalid_request`.
- **R-09** *Given* a quantity in a non-base unit, *then* it MUST be converted via `conversion_factor` and persisted in the base unit (RN-13); an unconvertible unit is refused.
- **R-10** Editing MUST be allowed **only** while `PENDING` and MUST replace the item set atomically, recomputing `total_amount`. *Given* an order in any other state, *then* `409 invalid_order_state` — after approval the order is a commitment, not a draft (PA-07).

### Approve or cancel an order (CU-COM-03, RF-COM-05, RN-15)

- **R-11** The state machine MUST be exactly: `PENDING → APPROVED`; `APPROVED → PARTIALLY_RECEIVED | RECEIVED`; `PARTIALLY_RECEIVED → PARTIALLY_RECEIVED | RECEIVED`; `PENDING | APPROVED | PARTIALLY_RECEIVED → CANCELLED`. `RECEIVED` and `CANCELLED` are terminal and accept no transition. No sixth state exists and none of the five is unreachable. *Given* a transition from a wrong source state, *then* `409 invalid_order_state`, nothing written.
- **R-12** Approval MUST move `PENDING → APPROVED` and only then enable reception (RN-15, HU-COM-01 second criterion). *Given* an `OPERATOR` attempting it, *then* `403` (HU-COM-01 fifth criterion, §2.3).
- **R-13** Cancellation MUST require a non-blank reason (F-3) and MUST accept no further reception (HU-COM-01 third criterion). *Given* a `PARTIALLY_RECEIVED` order, *then* cancellation is **allowed** and closes it: already-received stock is **not** reversed and no Kardex row is written or deleted (RN-12, RNF-INT-02). Without this, a half-fulfilled order could never be closed (PA-08).

### Register a goods reception and recalculate the WAC (CU-COM-04, RF-COM-02, RF-COM-04, RN-10)

- **R-14** Reception MUST be allowed only from `APPROVED` or `PARTIALLY_RECEIVED` (RN-15). *Given* a `PENDING`, `RECEIVED` or `CANCELLED` order, *then* `409 invalid_order_state` (CU-COM-04 EX-01).
- **R-15** In **one** transaction, per received line with `receivedQuantity > 0`, `applyMovement` MUST be called with `PURCHASE_RECEIPT`, the line's effective unit cost (R-17), `reference_type = 'PURCHASE_ORDER'` and `reference_id` = the order's `external_id`, so balance, average cost and Kardex move together (RN-02, RN-10, RNF-INT-01, HU-INV-03 third criterion).
- **R-16** *Given* a received quantity above the line's pending balance, *then* it is accepted **only** for `BRANCH_MANAGER` or `ADMIN`; an `OPERATOR` is refused with `403 over_receipt_requires_manager`, and the accepted excess MUST be recorded in the audit entry (CU-COM-04 FA-02, HU-COM-04 third criterion, F-7). A negative or non-numeric quantity is always refused.
- **R-17** The unit cost feeding RN-10 MUST be the line's **effective acquisition cost**, `unit_cost × (1 − discount_percent / 100)` — what the company actually pays — never the gross list cost (PA-09). *Given* the order line carries no usable cost, *then* the operation is blocked, never defaulted to zero (CU-COM-04 EX-02).
- **R-18** The new average MUST be exactly `((stock × CPP) + (quantity × cost)) / (stock + quantity)` (RN-10). *Given* 100 units at an average of 10 and a receipt of 100 at 20, *then* the new average is exactly 15 (HU-INV-03 second criterion). *Given* a prior balance of zero, *then* the new average is the received cost.
- **R-19** After the movements, `received_quantity` MUST be incremented per line and the order MUST become `RECEIVED` when **every** line's `received_quantity >= ordered_quantity`, and `PARTIALLY_RECEIVED` otherwise (CU-COM-04 step 8, HU-COM-04). *Given* 100 ordered and 60 received, *then* stock rises by 60, the order is `PARTIALLY_RECEIVED` and the pending balance is 40; *when* the remaining 40 arrive, *then* the order becomes `RECEIVED`.
- **R-20** A reception is **all-or-nothing across its lines**: any infrastructure failure MUST roll back stock, average cost, Kardex, `received_quantity` and the state change together (HU-INV-03 fifth criterion, EX-04 shape, RNF-INT-01).
- **R-21** *Given* two concurrent receptions against the same order, *then* the pessimistic lock serializes them and no line is double-counted; neither request answers `500` (T-02).
- **R-22** *Given* a line with `receivedQuantity = 0`, *then* it writes no Kardex row and does not move the average — `kardex_movements` has `CHECK (quantity > 0)`. A reception whose lines are all zero MUST be refused with `400 invalid_request`.
- **R-23** The receiving branch MUST be the session branch and MUST equal the order's branch (RN-14). *Given* an order of another branch, *then* `404 purchase_order_not_found`.

### Purchase history (CU-COM-05, RF-COM-03)

- **R-24** The listing MUST be paginated and filterable by supplier, product, status and date range, returning per order its number, supplier, status, `total_amount` and reception date (HU-COM-02 first criterion). The branch filter is **not** a client parameter: the caller's branch is the scope (R-25).
- **R-25** *Given* a `BRANCH_MANAGER` or `OPERATOR`, *then* only their own branch's orders are visible (HU-COM-02 third criterion, RNF-SEC-03). `ADMIN` reads network-wide (RN-08); an order of another branch answers `404 purchase_order_not_found`, never `403`, which would confirm it exists.
- **R-26** A per-product **agreed-cost history** MUST be available, returning each order line's effective unit cost over time with its order, supplier and date, so the cost evolution is visible (HU-COM-02 second criterion). It is a read: it never exposes `branch_inventories.average_cost`.
- **R-27** Order detail MUST return every line with ordered quantity, received quantity, pending balance, unit cost, discount and subtotal, plus the order's status, totals, payment terms and cancellation reason.

---

## 5. Authorization matrix

Cross-checked against `docs/casos_de_uso.md` §2.3. Enforced with `hasAuthority()` — never
`hasRole()`, which prepends `ROLE_`. "Own branch" is always the `AuthenticatedPrincipal` branch.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | Branch rule |
| :--- | :---: | :---: | :---: | :--- |
| Create / edit purchase order (CU-COM-02) | ✅\* | ✅ | ✅ | session branch; never a parameter |
| Approve order (CU-COM-03) | ✅ | ✅ | ❌ | actor's branch MUST equal the order's |
| Cancel order (CU-COM-03) | ✅ | ✅ | ❌ | actor's branch MUST equal the order's |
| Register reception (CU-COM-04) | ✅\* | ✅ | ✅ | session branch MUST equal the order's |
| Accept an over-receipt (R-16) | ✅ | ✅ | ❌ | as above |
| Read order / list / cost history (CU-COM-05) | ✅ | ✅ | ✅ | own branch; `ADMIN` network-wide |
| Read suppliers (CU-COM-01) | ✅ | ✅ | ✅ | corporate data, no branch scope |
| Create / edit / enable / disable supplier | ✅ | ❌ | ❌ | corporate, no branch scope |

**\*** A corporate `ADMIN` has no branch to derive, so creating an order or registering a reception
answers `403 branch_context_required` — the resolution `inventory`, `transfers` and `sales` already
took for session-scoped mutations, preserving RN-14 rather than reintroducing a client-supplied
branch. Approval and cancellation act on the stored order's branch, so `ADMIN` needs no branch
context there.

§2.3 grants "Crear órdenes de compra" and "Registrar recepción de mercancía" to all three roles, and
"Aprobar órdenes de compra" to `ADMIN` and `BRANCH_MANAGER` only. Supplier administration has **no
row** in §2.3; CU-COM-01's principal actor is the Administrador General and HU-COM-03 is written
from that role, so writes are `ADMIN`-only — decided by **who executes it in practice**, not by
analogy with another master-data entity. Reads stay open because every order creator needs the
supplier list (PA-06). Over-receipt authorization comes from CU-COM-04 FA-02, which names the
Gerente de Sucursal explicitly and has no §2.3 row of its own.

---

## 6. API surface

All identifiers are `external_id` UUIDs (RNF-API-02). Page envelope matches the existing
controllers: `{ content, totalElements, page, size }`; errors use `{ code, message }`. Quantities are
decimals in the base unit (RN-13); money is decimal, never floating point.

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/purchases/suppliers` | CU-COM-01 | `{ taxId, name, contactName?, email?, phone?, address? }` | `201` supplier |
| `GET` | `/api/purchases/suppliers` | CU-COM-01 | `active?`, `search?`, `page`, `size`, `sort` | page of suppliers |
| `GET` | `/api/purchases/suppliers/{externalId}` | CU-COM-01 | — | `200` supplier |
| `PUT` | `/api/purchases/suppliers/{externalId}` | CU-COM-01 | `{ name, contactName?, email?, phone?, address? }` | `200` supplier |
| `PATCH` | `/api/purchases/suppliers/{externalId}/disable` | R-03 | — | `200` supplier |
| `PATCH` | `/api/purchases/suppliers/{externalId}/enable` | R-03 | — | `200` supplier |
| `POST` | `/api/purchases/orders` | CU-COM-02 | `{ supplierExternalId, paymentTerms?, notes?, items: [{ productExternalId, quantity, unitOfMeasureExternalId?, unitCost, discountPercent? }] }` | `201` order detail |
| `GET` | `/api/purchases/orders` | CU-COM-05 | `supplierExternalId?`, `productExternalId?`, `status?`, `from?`, `to?`, `page`, `size`, `sort` (`createdAt`, `totalAmount`) | page of order summaries |
| `GET` | `/api/purchases/orders/{externalId}` | CU-COM-05 | — | order detail |
| `PUT` | `/api/purchases/orders/{externalId}` | R-10 | as `POST`, `PENDING` only | `200` order detail |
| `POST` | `/api/purchases/orders/{externalId}/approval` | CU-COM-03 | — | `200` order detail |
| `POST` | `/api/purchases/orders/{externalId}/cancellation` | CU-COM-03 | `{ reason }` | `200` order detail |
| `POST` | `/api/purchases/orders/{externalId}/receptions` | CU-COM-04 | `{ notes?, items: [{ itemExternalId, receivedQuantity, unitOfMeasureExternalId? }] }` | `200` order detail |
| `GET` | `/api/purchases/cost-history` | R-26 | `productExternalId` *(required)*, `supplierExternalId?`, `from?`, `to?`, `page`, `size` | page of `{ orderExternalId, orderNumber, supplier: { externalId, name }, unitCost, discountPercent, effectiveUnitCost, quantity, orderedAt, receivedAt }` |

Order detail: `{ externalId, orderNumber, status, branch: { externalId, name }, supplier:
{ externalId, taxId, name }, createdBy: { externalId, username }, paymentTerms, totalAmount, notes,
cancellationReason, createdAt, updatedAt, receivedAt, items: [{ externalId, productExternalId, sku,
name, orderedQuantity, receivedQuantity, pendingQuantity, unitCost, discountPercent,
effectiveUnitCost, subtotal }] }`. `notes` is the human portion with the F-3 token stripped. The
internal `order_number` follows `OC-<yyyy>-<nnnn>`, matching the `TRF-` and `VEN-` precedent. No
numeric `id` appears in any field, message or `Location` header, and no endpoint accepts the acting
branch (RN-14).

---

## 7. Error taxonomy

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` | 400 | bean-validation failure, malformed UUID, page size above cap, bad date range, empty item list, an all-zero reception (R-22) |
| `invalid_order_quantity` | 400 | R-05 — `ordered_quantity <= 0`, or a negative received quantity |
| `invalid_unit_cost` | 400 | R-05 / R-17 — negative or absent unit cost (CU-COM-04 EX-02) |
| `duplicate_order_item` | 400 | R-08 — the same product appears twice in one order |
| `discount_out_of_range` | 400 | R-05 — `discount_percent` outside `0 … 100` |
| `unit_conversion_unavailable` | 400 | R-09 — the supplied unit has no conversion to the base unit |
| `cancellation_reason_required` | 400 | R-13 — blank cancellation reason |
| `branch_context_required` | 403 | §5 — corporate `ADMIN` creating an order or receiving goods |
| `over_receipt_requires_manager` | 403 | R-16 — an `OPERATOR` receiving above the pending balance |
| `supplier_not_found` | 404 | unknown supplier `external_id` |
| `product_not_found` | 404 | a product `external_id` names nothing, or is disabled |
| `purchase_order_not_found` | 404 | unknown order, **or** one belonging to another branch (R-23, R-25) |
| `purchase_order_item_not_found` | 404 | a reception line names an item outside this order |
| `supplier_tax_id_already_exists` | 409 | R-01 — `suppliers.tax_id` is `UNIQUE` |
| `supplier_not_active` | 409 | R-04 — a disabled supplier named on a new order |
| `invalid_order_state` | 409 | R-10 / R-11 / R-14 — a transition, edit or reception from a state that forbids it (RN-15) |
| `concurrent_order_update` | 409 | the pessimistic lock could not be acquired within the timeout (T-02) |
| `duplicate_order_number` | 409 | F-9 — `order_number` `UNIQUE` violated despite the advisory lock (last line of defence, T-07) |

**Must not leak.** Whether an order exists in a branch the caller does not belong to (`404`, never
`403`); `branch_inventories.average_cost` or any resulting valuation — the cost history shows agreed
purchase costs, never the branch's weighted average; another branch's stock levels; numeric `id`
values anywhere; the raw F-3 `CANCEL_REASON` token; stack traces, SQL text, constraint names or JPA
exception messages.

---

## 8. Transactional and consistency guarantees

- **T-01** Atomic together per reception: every `applyMovement` call (balance + average cost + Kardex row), every `purchase_order_items.received_quantity` update, the `purchase_orders` status change with `received_at`, and the `audit_logs` entry (RN-02, RN-10, RF-VAL-02, RNF-INT-01). Same for creation, edit, approval and cancellation: state change plus audit entry, all or nothing.
- **T-02** Pessimistic locking REQUIRED: inside `StockMutationPort` on each `branch_inventories` row (already implemented), and on the `purchase_orders` row before every transition and reception (F-5). Inventory rows MUST be locked in a deterministic order — product `external_id` ascending — so two concurrent receptions over the same products cannot deadlock.
- **T-03** `audit_logs.branch_id` is the branch of the **mutated resource**: the order's branch for creation, approval, cancellation and reception. Supplier entries are corporate and carry a null branch.
- **T-04** `AFTER_COMMIT` only, in its own transaction: nothing in this module. `purchases` publishes no domain event — it raises no alert (§1) — so no `AFTER_COMMIT` listener exists to fail.
- **T-05** Reads — order listing, detail, cost history, supplier listing — are `readOnly` and take no lock (RN-09).
- **T-06** Reception is **not** idempotent and gains no deduplication key: two identical reception requests accumulate twice, which R-16 then gates as an over-receipt. Approval and cancellation are idempotent-by-refusal: a repeat lands on a state R-11 rejects with `409`. Order creation is not idempotent — two identical requests are two orders.
- **T-07** Database constraints are the last line of defence, never the first (RNF-INT-03): the domain refuses before the write for `CHECK (ordered_quantity > 0)`, `CHECK (received_quantity >= 0)`, `CHECK (discount_percent BETWEEN 0 AND 100)`, `CHECK (quantity > 0)` on `kardex_movements`, the `status` `CHECK`, `suppliers.tax_id UNIQUE` and `purchase_orders.order_number UNIQUE`.

---

## 9. Non-functional obligations

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-01 | p95 < 200 ms for order listings, detail and cost history | `idx_purchases_branch`, `idx_purchases_supplier`, `idx_purchases_status` and `idx_purchase_items_order` MUST be the access paths; items fetched one query per page — no N+1 |
| RNF-PER-02 | < 500 ms for a reception (CU-COM-04's own target) | one lock pass over the affected rows; no per-line lookup query; no event dispatch inside the transaction |
| RNF-PER-04 | every collection paginated, oversized page rejected | `400 invalid_request` (R-00, `DT-10`) |
| RNF-INT-01 | a reception is atomic across lines, balance, average cost and Kardex | T-01, proven by `PurchaseReceptionAtomicityIT` |
| RNF-INT-02 | Kardex append-only, including after a cancellation | R-13, R-20; replaying the history reproduces the balance |
| RNF-INT-03 | constraints are the last defence, not the first | T-07, asserted by refusing before the write in unit tests |
| RNF-SEC-01 | role checks with `hasAuthority()`, no `ROLE_` prefix | §5 matchers plus method-level checks |
| RNF-SEC-03 | branch isolation on read and write | §5, R-07, R-23, R-25; `purchase_order_not_found` over `403` |
| RNF-SEC-05 | all input validated in the backend | bean validation plus domain value objects before any write; totals recomputed server-side (R-06) |
| RNF-API-01 | OpenAPI documents each endpoint, its statuses and its error envelope | `/v3/api-docs` contains all fourteen operations |
| RNF-API-02 | only `external_id` on the wire | §6, asserted on response shape in the smoke tests |
| RNF-OBS-01 | structured logs carry correlation id, user, branch, operation | over-receipt acceptance logged with its authorizing role |
| RNF-MAN-01 | the state machine and RN-10 arithmetic covered by automated tests | §10 unit tests over every legal and illegal transition, and over the HU-INV-03 worked example |

---

## 10. Definition of done

Verifiable in the three planned PRs (`openspec/PLAN.md` §3).

**PR 1 — domain + application**

- [ ] `com.optiplant.inventory.purchases.domain` exists with no Spring or Jakarta import; `shared` still imports no module name; `ModuleBoundariesTest` passes unchanged (`purchases` already declared).
- [ ] The RN-10 recalculation lands **inside `inventory`** behind `StockMutationPort` (P-05), triggered only by `PURCHASE_RECEIPT` (P-06). No new `shared` port; `StockMutationPort` keeps its two methods.
- [ ] Unit `*Test` (no Docker) covering R-11 every legal and illegal transition including both terminal states, R-18 the HU-INV-03 worked example (100 @ 10 + 100 @ 20 → exactly 15) and the zero-balance case, R-17 effective-cost arithmetic, R-19 the partial/total completion rule, R-16 over-receipt gating by role, R-06 total arithmetic, R-09 unit conversion, R-13 the reason requirement, and R-05 item validation.
- [ ] A unit test asserting `TRANSFER_IN`, `ADJUSTMENT_POS` and `INITIAL_LOAD` leave `average_cost` untouched (P-06) — `add-sales-module` R-21 must keep holding.
- [ ] `cd backend && ./mvnw verify` green.

**PR 2 — infrastructure + web**

- [ ] Adapters, controllers, exception handler and `SecurityConfig` matchers for `/api/purchases/**`, using `hasAuthority()` (§5).
- [ ] `purchases` consumes `StockMutationPort` and implements none of it; it never writes `branch_inventories` or `kardex_movements` directly.
- [ ] `order_number` assigned under the `DT-11`/`DT-12` advisory-lock technique with format `OC-<yyyy>-<nnnn>` (F-9).
- [ ] Every §7 code is reachable from at least one controller path — no dead error code.
- [ ] `./scripts/validar_esquema.sh` green — expected **unaffected**, since no `backend/init-db/` file changes (§2.5). If it must change, §2.5 was wrong: stop and report.
- [ ] `cd backend && ./mvnw verify` green.

**PR 3 — verification** — Testcontainers `*IT` reserved for the invariants that can break the system.

- [ ] `PurchaseReceptionAtomicityIT` — R-15/R-18/R-20/T-01: stock incremented, `average_cost` recalculated to the RN-10 value, one `PURCHASE_RECEIPT` Kardex row per line with `reference_type = 'PURCHASE_ORDER'`; a forced mid-reception failure leaves order, `received_quantity`, balances, average cost and Kardex untouched.
- [ ] `PartialReceptionIT` — R-19/HU-COM-04: 100 ordered, 60 received → `PARTIALLY_RECEIVED`, pending 40; the remaining 40 → `RECEIVED`; a third reception is refused with `invalid_order_state`.
- [ ] `PurchaseOrderStateMachineIT` — R-11/R-12/R-14: reception refused from `PENDING`, from `RECEIVED` and from `CANCELLED` (RN-15); an `OPERATOR` refused the approval; cancellation from `PARTIALLY_RECEIVED` keeps received stock and writes no reversal row.
- [ ] `PurchaseConcurrencyIT` — R-21/T-02: two concurrent receptions on one order, no double count, no `500`.
- [ ] `PurchaseBranchIsolationIT` — R-23/R-25/§5: branch A gets `purchase_order_not_found` for branch B's order; `ADMIN` reads it.
- [ ] Smoke coverage of supplier CRUD, order listing with every filter, and the cost history (status, envelope shape, no numeric id, `average_cost` never exposed).
- [ ] `docs/deuda_tecnica.md` carries the **`DT-13`** fiche, its registry row and a changelog entry (F-9).
- [ ] `python3 scripts/validar_trazabilidad.py` green — **13 DT declared, 13 with fiche**; RF/RNF/RN/CU counts unchanged.
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

---

## 11. Open questions

None blocking. Nine decisions were taken here rather than escalated, each with its reversal cost.

- **PA-01 — The RN-10 recalculation lives inside `inventory`, behind `StockMutationPort.applyMovement`, with no new port and no signature change (P-05).** `StockMutationPolicy` already holds both operands the formula needs, and the row is already locked there; a separate `shared` valuation-write port would mean a second write and a second lock on `inventory`'s own row, and would let a caller move stock without moving cost — the exact drift RN-02's design forbids. Reversal: extract it into a `shared` valuation port plus one call site in `purchases`.
- **PA-02 — Over-receipt is accepted, gated by `BRANCH_MANAGER`/`ADMIN` authority and audited (R-16).** CU-COM-04 FA-02 and HU-COM-04's third criterion require exactly that, and unlike `transfers` — where RN-06 made over-receipt unrepresentable — `received_quantity` carries only `CHECK (>= 0)`, so the schema permits it (F-7). Reversal: refuse it outright with a `409` and route the excess through CU-INV-05.
- **PA-03 — A supplier's "condiciones comerciales" (RF-COM-06) is recorded per order in `purchase_orders.payment_terms`, not on the supplier (F-1).** `suppliers` has no column for it and no `notes` column to hold a token; the payment term is contractually meaningful on the order, which RF-COM-01 already requires. Reversal: add `suppliers.payment_terms` and prefill the order field from it.
- **PA-04 — `order_number` is `OC-<yyyy>-<nnnn>`, assigned with the `DT-11`/`DT-12` annual advisory-lock technique, registered as `DT-13` (F-9).** No sequence exists and §2.5 freezes the schema; the `UNIQUE` constraint stays as last defence. Reversal: `CREATE SEQUENCE purchase_order_number_seq` and drop the lock, per the `DT-13` payment plan.
- **PA-05 — There is no reception document; the reception history is the Kardex plus `received_quantity` (F-6).** A `purchase_order_receipts` table is a migration, and every fact a reception needs — quantity, cost, actor, timestamp, order reference — already lives on the `PURCHASE_RECEIPT` row. Reversal: a reception table plus its adapter; the Kardex rows already carry the data to backfill it.
- **PA-06 — Supplier writes are `ADMIN`-only; supplier reads are open to any authenticated user (§5).** §2.3 has no row for supplier administration, so the role is decided by who executes it: CU-COM-01's principal actor and HU-COM-03's narrator are both the Administrador General. Reads cannot be restricted — an `OPERATOR` who may create an order needs the supplier list. Reversal: one security matcher.
- **PA-07 — An order is editable only while `PENDING` (R-10).** RF-COM-01 requires editing but RF-COM-05 makes approval the gate that authorizes reception; editing an approved order would let quantities and costs change behind an approval that already happened. Reversal: allow editing in `APPROVED` with a forced return to `PENDING`.
- **PA-08 — Cancellation is allowed from `PARTIALLY_RECEIVED`, without reversing received stock (R-13).** HU-COM-01 names `PENDING` and `APPROVED` but forbids nothing else; without this a half-fulfilled order stays open forever, and reversing stock would contradict RN-12 and RNF-INT-02. Reversal: restrict cancellation to the two states and add a distinct short-close transition — which the five status literals cannot express without a migration.
- **PA-09 — RN-10 is fed the discount-adjusted effective unit cost, not the gross `unit_cost` (R-17).** RN-10 says "costo", and the weighted average must reflect what was actually paid; the gross figure would overstate inventory value and, through RN-03, every subsequent outbound movement's cost. It is also the figure `purchase_order_items.subtotal` already implies. Reversal: one expression in the reception service.
