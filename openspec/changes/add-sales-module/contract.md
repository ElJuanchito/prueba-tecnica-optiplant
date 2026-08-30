# Contract — `add-sales-module`

Acceptance contract for the `sales` and `pricing` module packages.
Step 1 of 3: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §4
(RN-01 … RN-17), `docs/casos_de_uso.md` §2.3, §3.4, §3.9 and §5 (CU-VEN-01), and
`docs/historias_de_usuario.md` HU-VEN-01 … HU-VEN-04 alongside this document.

---

## 1. Scope

Two module packages in one change: **`sales`** (sale registration with stock validation, receipt
query, voiding with stock reversal, external-POS intake) and **`pricing`** (price lists, prices with
optional branch exception and validity range, discount caps, and the price resolution `sales`
consumes). Two modules with independently verified ArchUnit boundaries, not one fused module.
`docs/decisiones_arquitectura_tecnica.md` §2.4 is unchanged. `sales` is the **second** consumer of
`shared/stock/StockMutationPort` — `transfers` proved it; `sales` builds none of it.

**Out of scope:** any schema change (§2.5, zero `backend/init-db/` edits); a customer entity or
segmentation by customer (`DT-04`); closing the overlapping-historical-price gap (`DT-03`); a
database guarantee that a frozen price matches its list (`DT-05` — domain plus tests only); writing
`branch_inventories` or `kardex_movements` directly; weighted-average-cost recalculation (RN-10 is
`purchases`' work); `PRICE_CHANGE` alerts (constant reserved, no `RF`/`HU` demands it); `CU-EXT-01`,
owned by `add-analytics-module`; sales dashboards (`CU-DSH-01` … `CU-DSH-03`) and the frontend.

---

## 2. Affected modules

### 2.1. Dependency directions

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `sales` | `shared` | `AuthenticatedPrincipal`, `Role`, `StockMutationPort`, `OutboundValuationPort`, `AuditWritePort`, price-resolution port (§2.3) | one-way |
| `pricing` | `shared` | `AuthenticatedPrincipal`, `Role`, `AuditWritePort`, price-resolution port | one-way |
| `sales` → `pricing` | — | the `shared` price-resolution port only | no direct edge |
| `sales` → `inventory` | — | `StockMutationPort` / `OutboundValuationPort` on `shared` | no direct edge |
| `sales` → `notifications` | — | `OperationalAlertRaised` published `AFTER_COMMIT` | no compile-time edge |

Every cross-module contact is a `shared` type, so the graph stays acyclic and `shared` keeps
importing no module name. `ModuleBoundariesTest.MODULOS` already lists `pricing` and `sales`
(`ModuleBoundariesTest.java:36-37`) — **no ArchUnit change is needed**. Foreign keys to `products`,
`branches` and `users` are resolved by each module's own persistence adapter with native
`external_id → id` queries, as `inventory` and `transfers` already do: no lookup port, no new edge.

### 2.2. Inherited decisions — not reopened

- **P-01/P-02** `StockMutationPort.applyMovement` mutates `current_stock` and inserts the Kardex row in the **caller's** transaction (RN-02, RNF-INT-01). Never `@Async`, never `AFTER_COMMIT`, never two writes. It takes the `branch_inventories` row lock itself.
- **P-03** `SALE` is outbound: `inventory` stamps the branch's `average_cost` (RN-03) and the caller MUST NOT supply a unit cost. The four inbound types — `ADJUSTMENT_POS` among them — REQUIRE one.
- **P-04** `StockMutationRejectedException.Reason.INSUFFICIENT_STOCK` is the port's refusal for an overdraw; each consumer maps it to its own `409`.
- **DT-10** Page size uses the `inventory`/`transfers` rejection pattern, never `catalog`'s silent clamp.

### 2.3. Decision — price resolution crosses as a `shared` port

**P-05** `pricing` MUST expose a synchronous `shared` port answering, for an applied list, a branch,
a set of products and an operation date: the resolved unit price per product plus the list's
`max_discount_percent`. Same shape and rationale as `shared/route/RouteLeadTimePort`. Resolution is
RN-16's — branch-specific beats corporate, only rows valid at the operation date are eligible. A
product with no eligible price returns empty: the port never invents a price and never falls back to
another list.

**P-06** Nothing crosses the other way. `pricing` MUST NOT read or write `sales` or `sale_items`;
`sales` is the only writer of both.

### 2.4. Decision — the external POS intake is an adapter, not a feature

**P-07** `CU-EXT-02` MUST be a **second primary adapter** — its own controller, DTOs and API-key
authentication — invoking the **same** use case `CU-VEN-01` uses, with zero new domain logic. If any
validation, price resolution, stock check or Kardex rule is restated there, the implementation is
wrong (RF-EXT-02: "aplicando exactamente las mismas validaciones"; CU-VEN-01 FA-03: steps 8–13 run
unchanged).

### 2.5. Schema findings — reported, not migrated

| # | Finding | Resolution inside the existing schema |
| :--- | :--- | :--- |
| **F-1** | `kardex_movements.movement_type` has **no sale-reversal constant** (`01-init-schema.sql:215-226`); adding one is an `ALTER`. | The reversal is an `ADJUSTMENT_POS` carrying `reference_type = 'SALE_VOID'` and `reference_id` = the sale's `external_id`, unambiguous in the Kardex, in audit and later in `analytics`. The original `SALE` row is untouched (RN-12, RNF-INT-02). |
| **F-2** | `ADJUSTMENT_POS` is inbound, so `StockMovementType.requiresSuppliedCost()` demands a unit cost — and the sale price is not it (RN-03). | Valued at the unit cost stamped on the original `SALE` movement, read back through `shared/stock/OutboundValuationPort` with `('SALE_INVOICE', saleExternalId)` — the port `transfers` already introduced for exactly this. No new port. |
| **F-3** | `sales` has **no `cancelled_at`, `cancelled_by` or `cancellation_reason`**. | Mandatory reason as a deterministic first-line token in `sales.notes` (`VOID_REASON:<text>`), the technique `transfers` uses for priority; actor and timestamp live in `audit_logs` (entity `SALE`), already exposed by `CU-SEG-04`. The API exposes `cancellationReason` and never leaks the token. |
| **F-4** | No table anywhere holds a **tax rate**, yet `sales.tax_amount` exists and RF-VEN-01 requires taxes. | `taxPercent` (0–100, default 0) arrives per sale; the backend computes `tax_amount` over the discounted subtotal. Monetary **totals are never accepted from the client**. |
| **F-5** | No column holds a **per-role discount cap**, yet HU-VEN-02 and CU-VEN-01 FA-02 speak of a role maximum. | `price_lists.max_discount_percent` is the single authoritative cap for every role (RN-17). Above it is refused whoever sends it; there is no approval-workflow entity to build (PA-02). |
| **F-6** | There is **no API-key table**, so `CU-EXT-02`'s credential cannot be persisted. | Keys are configuration-supplied (env / `application-*.yml`), each mapping to one branch `external_id` plus one service user `external_id` already in `users`. Branch comes from the credential, never the body (RN-14). Compared in constant time, never logged. |
| **F-7** | `sales` has **no version column**, so optimistic locking is unavailable for voiding. | Pessimistic row lock (`SELECT … FOR UPDATE`) on the `sales` row before the transition (T-02). |
| **F-8** | `sale_items` has no unit-of-measure column. | RN-13 settles it: quantities persist in the base unit; an alternative unit in the request is converted on entry via `conversion_factor` (CU-VEN-01 FA-01) and never stored. |

None of these blocks any of the five use cases. **No `backend/init-db/` change is proposed.**

### 2.6. Decision — the low-stock alert belongs behind the port

**P-08** HU-VEN-01's fifth criterion requires a restock alert when a sale drops the balance to or
below its threshold. Verified in the code: `AlertRaisingPolicy` is invoked only from `inventory`'s
`StockMovementService` and `StockThresholdService` — `StockMutationAdapter` raises nothing, so today
no port consumer produces `STOCK_MINIMUM` (a latent gap for `transfers` dispatch too). `sales` MUST
NOT close it by reading `branch_inventories`: that table belongs to `inventory` and the read creates
the edge §2.1 forbids. The evaluation MUST live behind the port, inside `inventory`, published
`AFTER_COMMIT` — one fix serving every consumer. No new port, no cycle, no schema change.

---

## 3. Traceability

Every identifier below was verified present in its source document.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| RF-VEN-01, RF-VEN-02 | CU-VEN-01 | HU-VEN-01 |
| RF-VEN-03 | CU-VEN-02 | HU-VEN-02 |
| RF-VEN-05 | CU-VEN-03 | HU-VEN-03 |
| RF-VEN-04 | CU-VEN-04 | HU-VEN-04 |
| RF-EXT-02 | CU-EXT-02 | — *(no HU exists; `Could` priority, SRS §5.3)* |
| RF-INV-06 | CU-VEN-01 *(a sale is an outbound movement)* | HU-VEN-01 |
| RF-INV-08 | traced by CU-VEN-01, CU-VEN-03 | HU-INV-02 |
| RF-VAL-01 | CU-VEN-01 → CU-ALE-01 *(threshold breach)* | HU-VEN-01, HU-ALE-01 |
| RF-VAL-02 | CU-VEN-02, CU-VEN-03 *(price changes and voidings, named verbatim in RF-VAL-02)* | HU-INV-04 |
| RN-01, RN-02, RN-03, RN-12, RN-13, RN-14, RN-16, RN-17 | constrain the above | — |

**No new `RF` / `RNF` / `RN` is required**, so `docs/` needs no edit and the traceability matrix of
`docs/casos_de_uso.md` gains no row. `python3 scripts/validar_trazabilidad.py` — verified green while
writing this contract (42 RF · 34 RNF · 17 RN · 37 CU · 11 DT) — must still pass unchanged.

---

## 4. Behavioural contract

**R-00** Every collection endpoint MUST paginate with a server-side maximum and MUST **reject** an
oversized page with `400 invalid_request` (`DT-10`). No endpoint accepts the acting branch in path,
query or body (RN-14).

### Register a sale (CU-VEN-01, RF-VEN-01, RF-VEN-02)

- **R-01** A sale MUST persist as `COMPLETED` with a unique `invoice_number`, the session branch, the acting user, the applied list, a mandatory customer name and optional tax id (`DT-04`), and at least one item with `quantity > 0` in the base unit (RN-13).
- **R-02** The acting branch MUST be `AuthenticatedPrincipal.branchId` (RN-14). *Given* a corporate `ADMIN` (`branchId == null`), *then* `403 branch_context_required` — there is no branch to derive.
- **R-03** In **one** transaction, per item, `applyMovement` MUST be called with `SALE`, no supplied cost (P-03), `reference_type = 'SALE_INVOICE'` and `reference_id` = the sale's `external_id`, so balance and Kardex move together (RN-02, RNF-INT-01).
- **R-04** *Given* insufficient stock on any item, *then* `409 insufficient_stock` naming product, requested and available quantity; **nothing** is persisted and no item is partially sold — the sale is one atomic act (RN-01, EX-01, HU-VEN-01).
- **R-05** *Given* two concurrent sales over the last available unit, *then* exactly one succeeds and the other is refused with `409 insufficient_stock`; stock never goes negative and neither request answers `500` (EX-02, HU-VEN-01).
- **R-06** *Given* an unknown, disabled or duplicated product, *then* refused with nothing written (EX-03).
- **R-07** *Given* a quantity in a non-base unit, *then* it MUST be converted via `conversion_factor` and persisted in the base unit (RN-13, FA-01); an unconvertible unit is refused.
- **R-08** A sale leaving `current_stock <= min_stock_threshold` MUST raise one `STOCK_MINIMUM` alert per affected product, `AFTER_COMMIT` (HU-VEN-01, RF-VAL-01, P-08). Its failure MUST NOT roll back the sale.
- **R-09** Any infrastructure failure between the balance write and the receipt MUST roll everything back: the Kardex is never left out of step with the balance (EX-04, RNF-INT-01).

### Price list and discounts (CU-VEN-02, RF-VEN-03)

- **R-10** The applied list MUST be the one named in the request or, when omitted, the branch's `default_price_list_id`, and MUST be active. *Given* neither, *then* `409 price_list_not_resolvable`. The list is a commercial choice (retail / wholesale / institutional), not a location, so accepting it does not weaken RN-14.
- **R-11** Per item the list price MUST be resolved through P-05 under RN-16. *Given* no eligible price, *then* `409 price_not_available` naming the product — the sale is refused, never priced at zero.
- **R-12** `sale_items.list_unit_price` MUST freeze the resolved price and `unit_price` the post-discount price. *Given* a later change to the list, *when* the sale is queried again, *then* its amounts are unchanged (`DT-05`).
- **R-13** `discount_percent` MUST be within `0 … 100` and MUST NOT exceed the applied list's `max_discount_percent` (RN-17, HU-VEN-02). *Given* a discount above the cap, *then* `400 discount_exceeds_cap` stating the cap — for **every** role (F-5). `unit_price` MUST NOT exceed `list_unit_price`.
- **R-14** The backend MUST compute `subtotal`, `discount_amount`, `tax_amount` and `total_amount` from quantities, discounts and `taxPercent`. *Given* monetary totals in the request, *then* they are ignored, never trusted (F-4, RNF-SEC-05).
- **R-15** Administration MUST be available: create/edit a list with its cap, deactivate it logically, and set a product price — corporate or with a branch exception — with a validity range (RF-VEN-03).
- **R-16** Setting a new price MUST close the current row (`valid_to`) and insert the new one in one transaction, never editing a historical row. *Given* a change leaving two rows with `valid_to IS NULL` for the same list, product and branch scope, *then* `409 price_period_conflict`. Overlapping **historical** ranges stay unrestricted — `DT-03`, accepted, not closed here.
- **R-17** Every price-list and price mutation MUST write an audit entry: RF-VAL-02 names price changes explicitly.

### Void a sale (CU-VEN-03, RF-VEN-05)

- **R-18** Allowed only from `COMPLETED`, moving the sale to `CANCELLED` with a mandatory non-blank reason (F-3). *Given* an already `CANCELLED` sale, *then* `409 invalid_sale_state` (HU-VEN-03).
- **R-19** In the **same** transaction, per item, the sold quantity MUST return through `applyMovement` with `ADJUSTMENT_POS`, `reference_type = 'SALE_VOID'`, `reference_id` = the sale's `external_id` (F-1), valued at the original `SALE` movement's unit cost (F-2, RN-03).
- **R-20** The original `SALE` rows MUST NOT be deleted or edited (RN-12, RNF-INT-02). *Given* the Kardex is replayed from zero after a void, *then* the running balance MUST equal `branch_inventories.current_stock` (HU-VEN-03).
- **R-21** The void MUST NOT alter `average_cost`: RN-10 recalculates only on valued inbound receipts, and reinstating stock at its own original cost leaves the weighted average untouched.
- **R-22** *Given* an `OPERATOR`, *then* `403` — voiding is manager-only (§5, HU-VEN-03).

### Receipt and detail query (CU-VEN-04, RF-VEN-04)

- **R-23** A sale MUST be retrievable by `external_id` **and** by `invoice_number`, returning branch, date, responsible user, customer, items with quantity, list price, applied price, discount and subtotal, plus discounts, taxes, total and status (HU-VEN-01, HU-VEN-04).
- **R-24** The listing MUST be paginated, filterable by date range and status, and MUST return aggregate totals for the filtered set (HU-VEN-04).
- **R-25** *Given* an actor of branch A querying a sale of branch B, *then* `404 sale_not_found` — never `403`, which would confirm the receipt exists (RNF-SEC-03). `ADMIN` reads network-wide (RN-08).

### External POS intake (CU-EXT-02, RF-EXT-02)

- **R-26** A second controller MUST accept a sale from an authenticated external system and invoke the same use case R-01 … R-14 govern (P-07); every rule above applies unchanged.
- **R-27** Branch and responsible user MUST come from the API credential, never the payload (RN-14, F-6). *Given* a body field naming a branch, *then* `400 invalid_request`.
- **R-28** *Given* an absent, malformed or unknown key, *then* `401 invalid_api_credential`, with no hint as to which part failed.
- **R-29** The POS MUST supply its own receipt number, persisted as `invoice_number`. *Given* a retry carrying a stored number, *then* `409 duplicate_invoice_number` and no second sale: the `UNIQUE` constraint makes the intake idempotent-by-refusal without a new column (T-06).

---

## 5. Authorization matrix

Cross-checked against `docs/casos_de_uso.md` §2.3. Enforced with `hasAuthority()` — never
`hasRole()`, which prepends `ROLE_`. "Own branch" is always the `AuthenticatedPrincipal` branch.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | External | Branch rule |
| :--- | :---: | :---: | :---: | :---: | :--- |
| Register sale (CU-VEN-01) | ✅\* | ✅ | ✅ | ✅ | session branch; never a parameter |
| Void sale (CU-VEN-03) | ✅ | ✅ | ❌ | ❌ | actor's branch MUST equal the sale's branch |
| Read sale / receipt (CU-VEN-04) | ✅ | ✅ | ✅ | ❌ | own branch; `ADMIN` network-wide |
| List sales (CU-VEN-04) | ✅ | ✅ | ✅ | ❌ | own branch; `ADMIN` network-wide |
| Resolve prices (CU-VEN-02, read) | ✅ | ✅ | ✅ | ❌ | session branch |
| Read price lists / prices | ✅ | ✅ | ✅ | ❌ | corporate data, no branch scope |
| Create / edit / deactivate price list | ✅ | ❌ | ❌ | ❌ | corporate, no branch scope |
| Set / close a product price | ✅ | ❌ | ❌ | ❌ | corporate, including branch exceptions |
| POS intake (CU-EXT-02) | ❌ | ❌ | ❌ | ✅ | branch bound to the API key (F-6) |

**\*** A corporate `ADMIN` has no branch to derive, so registering a sale answers
`403 branch_context_required` — the resolution `inventory` and `transfers` already took for
session-scoped mutations, preserving RN-14 rather than reintroducing a client-supplied branch. For a
void the acting branch is the stored sale's, so `ADMIN` needs no branch context and
`AuthenticatedPrincipal.mayMutateBranch` already grants it.

§2.3 grants "Registrar ventas" to all three roles plus the external system, and "Anular ventas" to
`ADMIN` and `BRANCH_MANAGER` only. Price administration has **no row** in §2.3; its nearest
comparable, "Gestionar catálogo maestro", is `ADMIN`-only. Writes are therefore `ADMIN`-only, while
reads stay open to any authenticated user because every seller needs the resolved price (PA-03).

---

## 6. API surface

All identifiers are `external_id` UUIDs (RNF-API-02). Page envelope matches the existing
controllers: `{ content, totalElements, page, size }`; errors use `{ code, message }`. Quantities are
decimals in the base unit (RN-13); money is decimal, never floating point.

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/sales` | CU-VEN-01 | `{ priceListExternalId?, customerName, customerTaxId?, taxPercent?, notes?, items: [{ productExternalId, quantity, unitOfMeasureExternalId?, discountPercent? }] }` | `201` sale detail |
| `GET` | `/api/sales` | CU-VEN-04 | `status?`, `from?`, `to?`, `page`, `size`, `sort` (`createdAt`, `totalAmount`) | page of summaries + `aggregates: { salesCount, totalAmount }` |
| `GET` | `/api/sales/{externalId}` | CU-VEN-04 | — | sale detail |
| `GET` | `/api/sales/by-invoice/{invoiceNumber}` | HU-VEN-04 | — | sale detail |
| `POST` | `/api/sales/{externalId}/cancellation` | CU-VEN-03 | `{ reason }` | `200` sale detail |
| `POST` | `/api/pricing/quotes` | CU-VEN-02 preload | `{ priceListExternalId?, items: [{ productExternalId, quantity, discountPercent? }] }` | `200 { priceListExternalId, code, maxDiscountPercent, items: [{ productExternalId, listUnitPrice, unitPrice, subtotal }] }` |
| `POST` | `/api/pricing/price-lists` | RF-VEN-03 | `{ code, name, description?, maxDiscountPercent }` | `201` price list |
| `GET` | `/api/pricing/price-lists` | RF-VEN-03 | `active?`, `page`, `size` | page of price lists |
| `PUT` | `/api/pricing/price-lists/{externalId}` | RF-VEN-03 | `{ name, description?, maxDiscountPercent }` | `200` price list |
| `PATCH` | `/api/pricing/price-lists/{externalId}/deactivation` | R-15 | — | `200` price list |
| `POST` | `/api/pricing/price-lists/{externalId}/prices` | R-16 | `{ productExternalId, branchExternalId?, unitPrice, validFrom? }` | `201` price *(closes the previous current row)* |
| `GET` | `/api/pricing/price-lists/{externalId}/prices` | R-15 | `productExternalId?`, `branchExternalId?`, `currentOnly?`, `page`, `size` | page of prices |
| `PATCH` | `/api/pricing/prices/{externalId}/closure` | R-16 | `{ validTo? }` | `200` price |
| `POST` | `/api/external/sales` | CU-EXT-02 | header `X-Api-Key`; body as `POST /api/sales` **plus** `invoiceNumber`, **minus** any branch field | `201` sale detail |

Sale detail: `{ externalId, invoiceNumber, status, branch: { externalId, name }, soldBy: { externalId,
username }, priceList: { externalId, code, maxDiscountPercent }, customerName, customerTaxId,
subtotal, discountAmount, taxAmount, totalAmount, notes, cancellationReason, createdAt, items:
[{ externalId, productExternalId, sku, name, quantity, listUnitPrice, unitPrice, discountPercent,
subtotal }] }`. `notes` is the human portion with the F-3 token stripped. The internal
`invoice_number` follows `VEN-<yyyy>-<nnnn>`, matching the `TRF-<yyyy>-<nnnn>` precedent. No numeric
`id` appears in any field, message or `Location` header, and no endpoint accepts the acting branch
(RN-14).

---

## 7. Error taxonomy

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` | 400 | bean-validation failure, malformed UUID, page size above cap, bad date range, `taxPercent` outside 0–100, a branch field in the POS payload (R-27) |
| `invalid_sale_quantity` | 400 | R-01 — quantity `<= 0` |
| `duplicate_sale_item` | 400 | R-06 — the same product appears twice |
| `discount_exceeds_cap` | 400 | R-13 / RN-17 — discount above the applied list's cap, or above 100 |
| `unit_conversion_unavailable` | 400 | R-07 — the supplied unit has no conversion to the base unit |
| `sale_reason_required` | 400 | R-18 — blank cancellation reason |
| `invalid_api_credential` | 401 | R-28 — absent, malformed or unknown API key |
| `branch_context_required` | 403 | §5 — corporate `ADMIN` registering a sale |
| `cross_branch_access_denied` | 403 | R-22 — voiding a sale of another branch |
| `product_not_found` | 404 | a product `external_id` names nothing, or is disabled |
| `price_list_not_found` | 404 | unknown price list |
| `price_not_found` | 404 | unknown price row on closure |
| `sale_not_found` | 404 | unknown sale, **or** one belonging to another branch (R-25) |
| `invalid_sale_state` | 409 | R-18 — voiding a sale that is not `COMPLETED` |
| `insufficient_stock` | 409 | R-04 / RN-01 — the sale would drive stock below zero |
| `price_list_not_resolvable` | 409 | R-10 — no list named and the branch has no active default |
| `price_not_available` | 409 | R-11 / RN-16 — no eligible price for the product in the applied list at the operation date |
| `price_period_conflict` | 409 | R-16 — a second current price for the same list, product and branch scope |
| `price_list_code_already_exists` | 409 | R-15 — `price_lists.code` is `UNIQUE` |
| `duplicate_invoice_number` | 409 | R-29 — POS retry with a stored receipt number |
| `concurrent_sale_update` | 409 | the pessimistic lock could not be acquired within the timeout (T-02) |

**Must not leak.** Whether a sale exists in a branch the caller does not belong to (`404`, never
`403`); the Kardex unit cost, the branch's `average_cost` or any valuation — a receipt shows prices,
never costs; another branch's stock levels beyond the requested-vs-available pair of the refused
item; numeric `id` values anywhere; the raw F-3 `VOID_REASON` token; API-key material in logs,
messages or traces; which half of a credential was wrong (R-28); stack traces, SQL text, constraint
names or JPA exception messages.

---

## 8. Transactional and consistency guarantees

- **T-01** Atomic together per sale: the `sales` row, every `sale_items` row, every `applyMovement` call and the `audit_logs` entry (RN-02, RF-VAL-02, RNF-INT-01). Same for a void: status change, reason, every reversal movement, audit entry.
- **T-02** Pessimistic locking REQUIRED: inside `StockMutationPort` on each `branch_inventories` row (already implemented), and on the `sales` row before a void (F-7). Inventory rows MUST be locked in a deterministic order — product `external_id` ascending — so two concurrent sales over the same products cannot deadlock.
- **T-03** `audit_logs.branch_id` is the branch of the **mutated resource**: the sale's branch for registration and voiding. Price-list entries are corporate and carry a null branch, except a branch-scoped price, which carries the branch it prices.
- **T-04** `AFTER_COMMIT` only, in its own transaction: the `STOCK_MINIMUM` alert (R-08, P-08). Its failure MUST be logged (RNF-OBS-01) and MUST NOT roll back the sale or surface to the caller.
- **T-05** Reads — listing, detail, receipt lookup, quotes, price listings — are `readOnly` and take no lock (RN-09).
- **T-06** `/api/sales` is **not** idempotent and gains no deduplication key: two identical requests are two sales. The POS intake **is** idempotent-by-refusal through the supplied `invoice_number` and its `UNIQUE` constraint (R-29). Voiding is idempotent-by-refusal: a repeat lands on `CANCELLED` and R-18 answers `409`.
- **T-07** Database constraints are the last line of defence, never the first (RNF-INT-03): the domain refuses before the write for `CHECK (current_stock >= 0)`, `check_applied_price_not_above_list`, `uq_price_current_branch`, `uq_price_current_corporate` and `sales.invoice_number UNIQUE`.

---

## 9. Non-functional obligations

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-01 | p95 < 200 ms for listings, detail, receipt lookup and quotes | `idx_sales_branch_date`, `idx_sales_invoice`, `idx_sale_items_sale`, `idx_price_list_items_lookup` MUST be the access paths; items fetched one query per page — no N+1 |
| RNF-PER-02 | < 500 ms for sale registration (CU-VEN-01's own target) | one price-resolution call for the whole basket, never one per item; locked rows only; no event dispatch inside the transaction |
| RNF-PER-04 | every collection paginated, oversized page rejected | `400 invalid_request` (R-00, `DT-10`) |
| RNF-INT-01 | a sale is atomic across items, balance and Kardex | T-01, proven by `SaleRegistrationAtomicityIT` |
| RNF-INT-02 | Kardex append-only through a void | R-20, proven by replaying history against the balance |
| RNF-SEC-01 | role checks with `hasAuthority()`, no `ROLE_` prefix | §5 matchers plus method-level checks |
| RNF-SEC-03 | branch isolation on read and write | §5, R-02, R-25; `sale_not_found` over `403` |
| RNF-SEC-05 | all input validated in the backend | bean validation plus domain value objects before any write; totals recomputed server-side (R-14) |
| RNF-API-01 | OpenAPI documents each endpoint, its statuses and its error envelope | `/v3/api-docs` contains all fourteen operations |
| RNF-API-02 | only `external_id` on the wire | §6, asserted on response shape in the smoke tests |
| RNF-OBS-01 | structured logs carry correlation id, user, branch, operation | alert-publication failures logged, never swallowed; API-key material never logged |
| RNF-MAN-01 | pricing and void rules covered by automated tests | §10, including `DT-05`'s frozen-price mitigation |

---

## 10. Definition of done

Verifiable in the three planned PRs (`openspec/PLAN.md` §3).

**PR 1 — domain + application**

- [ ] `com.optiplant.inventory.sales.domain` and `…pricing.domain` exist with no Spring or Jakarta import; the `shared` price-resolution port (P-05) exists and `shared` still imports no module name.
- [ ] Unit `*Test` (no Docker) covering R-11 RN-16 resolution (branch beats corporate; an expired row is ineligible), R-13 RN-17 at, below and above the cap, R-14 total arithmetic including tax, R-12 `DT-05`'s mitigation (a `list_unit_price` inconsistent with the resolved price is rejected by the domain), R-18 the void state machine including the double void, R-07 unit conversion, and R-01 quantity and item validation.
- [ ] `cd backend && ./mvnw verify` green.

**PR 2 — infrastructure + web**

- [ ] Adapters, controllers, exception handler and `SecurityConfig` matchers for `/api/sales/**`, `/api/pricing/**` and `/api/external/sales`, using `hasAuthority()` (§5).
- [ ] `pricing` implements the P-05 port; `sales` consumes it, plus `StockMutationPort` and `OutboundValuationPort`, and implements neither.
- [ ] The API-key filter (F-6) resolves branch and service user from configuration; no branch field is read from the POS body (R-27).
- [ ] The `STOCK_MINIMUM` evaluation is reachable from the port path (P-08).
- [ ] Every §7 code is reachable from at least one controller path — no dead error code.
- [ ] `./scripts/validar_esquema.sh` green — expected **unaffected**, since no `backend/init-db/` file changes (§2.5). If it must change, §2.5 was wrong: stop and report.
- [ ] `cd backend && ./mvnw verify` green.

**PR 3 — verification** — Testcontainers `*IT` reserved for the invariants that can break the system.

- [ ] `SaleRegistrationAtomicityIT` — R-03/R-04/T-01: stock decremented and one `SALE` Kardex row per item with `reference_type = 'SALE_INVOICE'`; a forced mid-sale failure leaves sale, balances and Kardex untouched.
- [ ] `SaleConcurrencyIT` — R-05/T-02: two concurrent sales over the last unit, exactly one succeeds, stock never negative, no `500`.
- [ ] `SaleVoidReversalIT` — R-19/R-20/R-21: the void adds an `ADJUSTMENT_POS` with `reference_type = 'SALE_VOID'` at the original unit cost, the original `SALE` row survives unchanged, replaying the Kardex reproduces `current_stock`, and `average_cost` is unmoved.
- [ ] `SaleBranchIsolationIT` — R-25/§5: branch A gets `sale_not_found` for branch B's sale; an `OPERATOR` is refused the void.
- [ ] `PriceResolutionIT` — R-11/R-16/RN-16: branch exception beats corporate, an expired price is ignored, a second current price is refused with `price_period_conflict`.
- [ ] `ExternalSaleIntakeIT` — R-26/R-27/R-29: the POS path produces the same rows as the internal path, ignores any branch in the body, and refuses a retried receipt number.
- [ ] Smoke coverage of the read endpoints and price-list CRUD (status, envelope shape, no numeric id).
- [ ] `python3 scripts/validar_trazabilidad.py` green (§3: no `docs/` edit expected).
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

---

## 11. Open questions

None blocking. Six decisions were taken here rather than escalated, each with its reversal cost.

- **PA-01 — The void reverses as `ADJUSTMENT_POS` with `reference_type = 'SALE_VOID'` (F-1, F-2).** The `movement_type` `CHECK` has no reversal constant and the schema is frozen; the reference columns keep it unambiguous and `OutboundValuationPort` supplies the original cost, so valuation does not drift. Reversal: a migration adding a `SALE_RETURN` constant plus a mapper change.
- **PA-02 — `price_lists.max_discount_percent` is the only discount cap, for every role (F-5).** RN-17 states the list cap categorically and no per-role cap exists in the schema; HU-VEN-02's "manager authorization" is satisfied by the cap itself, not by an approval workflow with no table to live in. Reversal: a per-role cap column plus one domain check.
- **PA-03 — Price administration is `ADMIN`-only; reads are open to any authenticated user.** §2.3 has no row for it and the nearest analogue, master catalog, is `ADMIN`-only; every seller needs the resolved price, so reads cannot be restricted. Reversal: one security matcher.
- **PA-04 — The applied list may be named in the request, defaulting to the branch's `default_price_list_id` (R-10).** RF-VEN-03 makes the list a commercial choice, not a location, so accepting it does not weaken RN-14 — the *branch* still comes only from the session. Reversal: drop the field and always use the branch default.
- **PA-05 — The POS supplies its own `invoice_number`, making the intake idempotent-by-refusal (R-29).** Without it a retried POS message would duplicate a sale and double-decrement stock, and the alternative — an idempotency-key table — is a migration. Reversal: generate the number server-side and add a deduplication column.
- **PA-06 — `taxPercent` arrives per sale and the backend computes `tax_amount` (F-4).** No tax catalog exists anywhere in the schema and RF-VEN-01 requires taxes on the receipt; accepting a computed amount from the client would violate RNF-SEC-05. Reversal: a tax-rate table plus resolution by product or branch.
