# Contract — `add-inventory-module`

Acceptance contract for the `inventory` and `notifications` module packages.
Step 1 of 3: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §4
(RN-01 … RN-17), `docs/casos_de_uso.md` §2.3 and §3, and `docs/decisiones_arquitectura_tecnica.md` §2.4
alongside this document.

---

## 1. Scope

Two module packages, created in one change: **`inventory`** (per-branch balances, Kardex,
manual adjustments, write-offs, minimum-stock thresholds, network availability) and
**`notifications`** (persistent operational alerts and their resolution). They are two
modules with independently verified ArchUnit boundaries, not one fused module.
`docs/decisiones_arquitectura_tecnica.md` §2.4 is unchanged.

This change also closes the three cross-module decisions that `purchases`, `sales` and
`transfers` will inherit: the synchronous stock-mutation port (§2.2), the in-transit stock
model (§2.3), and the alert event contract (§2.4).

**Out of scope**

- Any schema change: `01-init-schema.sql` already ships the three tables and this contract adapts
  to them (§2.5).
- Weighted-average-cost recalculation on goods receipt (RN-10) — `inventory` owns `average_cost`
  and stamps it on outbound movements; the inbound revaluation path belongs to CU-COM-04.
- Producers of `LOGISTIC_DELAY` and `TRANSFER_DISCREPANCY` alerts, and any scheduler. Their event
  shapes are contracted (§2.4) so `logistics` and `transfers` need no new agreement;
  `STOCK_MINIMUM` is the only one fully produced here.
- `reserved_stock` writes (exposed read-only per RF-INV-03; `sales` writes it), dashboards over
  this data (CU-DSH-02, `analytics`), and the frontend.

---

## 2. Affected modules

### 2.1. Dependency directions

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `inventory` | `shared` | `AuthenticatedPrincipal`, `Role`, `AuditWritePort`, `StockMutationPort` | one-way |
| `notifications` | `shared` | `AuthenticatedPrincipal`, `Role` | one-way |
| `catalog` | `shared` ← `inventory` | `ProductStockPresencePort` (already declared, unimplemented) | no direct edge |
| `inventory` → `notifications` | — | `AFTER_COMMIT` domain event on `shared` | no compile-time edge |

No module imports another module's package; every cross-module contact is a `shared` type, so the
graph stays acyclic and `ModuleBoundariesTest` — whose `MODULOS` array already lists both
`inventory` and `notifications` (`ModuleBoundariesTest.java:35`) — needs no new rule.

`inventory` MUST ship the adapter implementing `shared/stock/ProductStockPresencePort`, turning
`ProductAdminService`'s fail-closed `StockPresence.UNKNOWN` placeholder into a real answer.

### 2.2. Decision — the stock mutation port

**`shared/stock/StockMutationPort`**, framework-free, `external_id`-shaped `UUID`s only, invoked
inside the caller's own transaction — same shape and rationale as `shared/audit/AuditWritePort`
and `shared/stock/ProductStockPresencePort`, so `purchases`, `sales` and `transfers` write stock
without importing `inventory` and the graph gains only `X → shared ← inventory`.

- **P-01** One write operation MUST, in a single call inside the caller's transaction, mutate
  `branch_inventories.current_stock` **and** insert the matching `kardex_movements` row (RN-02,
  RNF-INT-01). No implementation may be `@Async`, `AFTER_COMMIT`, or split the two writes.
- **P-02** The command MUST carry branch and product `external_id`, movement type (one of the eight
  `movement_type` values), quantity **strictly positive and in the product's base unit** (RN-13 —
  `CHECK (quantity > 0)` makes the sign a property of the type, never of the number), optional unit
  cost, reference type and id, notes, and the responsible user `external_id`.
- **P-03** Unit cost MUST be supplied for valued inbound types (`PURCHASE_RECEIPT`, `TRANSFER_IN`,
  `ADJUSTMENT_POS`, `INITIAL_LOAD`) and MUST be absent for outbound ones (`SALE`, `TRANSFER_OUT`,
  `DAMAGE_WASTE`, `ADJUSTMENT_NEG`), where `inventory` stamps the branch's current `average_cost`
  (RN-03). Either violation MUST be rejected.
- **P-04** The port MUST return the created movement's `external_id`, and MUST expose no read
  operation — reads belong to `inventory`'s inbound ports and §6.

### 2.3. Decision — in-transit stock

`branch_inventories.in_transit_stock` is the **destination** branch's incoming quantity, per RN-04
("no vendible en la sucursal destino hasta la confirmación de recepción") and RF-INV-03, which
lists in-transit as a per-branch readable balance.

- **P-05** Dispatch (CU-TRA-03) is two effects in one transaction: a `TRANSFER_OUT` movement on the
  **origin** row through P-01, and an increment of `in_transit_stock` on the **destination** row.
  Receipt (CU-TRA-05) decrements that `in_transit_stock` and applies `TRANSFER_IN` on the
  destination row, likewise in one transaction.
- **P-06** The in-transit increment/decrement is **not** a stock mutation and MUST NOT write a
  Kardex row — `current_stock` is untouched, so RN-02 is not engaged. It is a second, explicitly
  named operation on `StockMutationPort`, never a side effect of P-01.
- **P-07** Available-to-sell is `current_stock − reserved_stock`; `in_transit_stock` is excluded
  from it at the destination until receipt (RN-04).

### 2.4. Decision — the alert event

A failed alert MUST NOT roll back the movement that triggered it, so alert creation travels as a
domain event published **`AFTER_COMMIT`** — which is why `notifications` is a separate package.

- **P-08** `inventory` MUST publish a `StockThresholdBreached` event after commit whenever a
  committed movement leaves `current_stock <= min_stock_threshold`, carrying branch and product
  `external_id`, resulting stock, threshold, and the movement `external_id`.
- **P-09** The event type MUST live in `shared`, so `transfers` and `logistics` can later publish
  `TRANSFER_DISCREPANCY` / `LOGISTIC_DELAY` against the same listener with no new agreement
  (RN-07 keeps discrepancy alerts obligatory in that future change).
- **P-10** The `notifications` listener MUST run in its own transaction; its failure MUST be logged
  (RNF-OBS-01) and MUST NOT surface to the caller of the mutation.

### 2.5. Schema findings — reported, not migrated

| # | Finding | Resolution inside the existing schema |
| :--- | :--- | :--- |
| **F-1** | `system_alerts` has **no `product_id`** and no dedup key, yet HU-ALE-01 forbids duplicating an unresolved alert for a persisting condition. | Deduplication keys on `(branch_id, alert_type, is_resolved = false)` plus a deterministic subject token that the producer MUST write into `title` as `STOCK_MINIMUM:<product external_id>` (fits `VARCHAR(150)`). `message` stays human-readable. |
| **F-2** | `branch_inventories` has **no version column**, so optimistic locking is unavailable. | Pessimistic row lock (`SELECT … FOR UPDATE`), which is what CU-INV-05 step 5 already prescribes. |
| **F-3** | No `branch_inventories` row exists before a product's first movement in a branch. | Threshold updates and adjustments MUST create the row on demand with zeroed balances; `uq_branch_product` makes the upsert safe under concurrency. |
| **F-4** | `kardex_movements` has **no unit-of-measure column**. | Confirms RN-13: everything is stored in the base unit. Alternative units convert at the API boundary only. |

None of these blocks any of the eight use cases. **No `backend/init-db/` change is proposed.**

---

## 3. Traceability

Every identifier below was verified present in its source document.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| RF-INV-03 | CU-INV-03, CU-INV-04 | HU-INV-01 |
| RF-INV-04 | CU-INV-04 | HU-INV-01 |
| RF-INV-05 | CU-INV-05 | HU-INV-04 |
| RF-INV-06 | CU-INV-05, CU-INV-06 | HU-INV-04, HU-INV-05 |
| RF-INV-07 | CU-INV-07, CU-ALE-01 | HU-DSH-02, HU-ALE-01 |
| RF-INV-08 | CU-INV-08 | HU-INV-02 |
| RF-VAL-01 | CU-ALE-01, CU-ALE-02 | HU-ALE-01, HU-ALE-02 |
| RF-VAL-02 | CU-INV-05 | HU-INV-04 |
| RN-01, RN-02, RN-08, RN-09, RN-11, RN-12, RN-13, RN-14 | constrain the above | — |
| RN-03, RN-04 | consumed by later modules through §2.2 / §2.3 | — |

**No new `RF` / `RNF` / `RN` is required**, so `docs/` needs no edit and the traceability matrix of
`docs/casos_de_uso.md` gains no row. `python3 scripts/validar_trazabilidad.py` — verified green
while writing this contract — must still pass unchanged.

---

## 4. Behavioural contract

**R-00 (cross-cutting)** Every collection endpoint MUST paginate with a server-side maximum page
size; *given* a page size above the cap, *then* `invalid_request` (RNF-PER-04). No request MUST
accept a branch in path, query or body (RN-14).

### Own-branch stock query (CU-INV-03)

- **R-01** MUST return, per product, `current_stock`, `reserved_stock`, `in_transit_stock`,
  `min_stock_threshold` and `average_cost` for the session-derived branch. *Given* an operator of
  branch A, *then* only branch A rows are returned.
- **R-02** *Given* a product with no `branch_inventories` row in that branch, *then* it is absent
  or reported with zeroed balances (F-3) — never a `500`.

### Network availability (CU-INV-04)

- **R-03** MUST return the product's balances in **every active branch**, read-only (RN-08).
  *Given* a `BRANCH_MANAGER` of branch A reading branch B, *then* the read succeeds and no mutation
  path exists for B.
- **R-04** The response MUST mark the caller's own branch (HU-INV-01); for a corporate `ADMIN` the
  marker MUST be absent, not fabricated.
- **R-05** *Given* zero stock across the whole network, *then* an explicit empty-availability
  result, not a `404`. The query MUST take no lock (RN-09).

### Manual adjustment (CU-INV-05)

- **R-06** MUST take the **counted physical quantity**, derive the difference against the locked
  balance, and emit `ADJUSTMENT_POS` / `ADJUSTMENT_NEG`. *Given* a balance of 100 and a count of 92,
  *then* one `ADJUSTMENT_NEG` of quantity 8 and a resulting balance of 92.
- **R-07** A non-blank reason MUST be mandatory (RN-11); *given* a blank or absent one, *then*
  refused with nothing written.
- **R-08** *Given* a count equal to the current balance, *then* refused: quantity 0 violates
  `CHECK (quantity > 0)` and a no-op is not an audit event. A negative count MUST be refused before
  reaching the database (RN-01).
- **R-09** Balance update, Kardex insert and audit entry MUST commit or fail together
  (RN-02, RF-VAL-02, RNF-INT-01).
- **R-10** *Given* an `OPERATOR`, *then* `403` and the attempt is recorded (CU-INV-05 EX-03).

### Write-off — damage, waste, expiry (CU-INV-06)

- **R-11** MUST emit `DAMAGE_WASTE` for a strictly positive quantity with a mandatory reason.
  *Given* a quantity above available stock, *then* `insufficient_stock`, nothing written (RN-01).
- **R-12** MUST be valued at the branch's current `average_cost` (RN-03, HU-INV-05), never at a
  client-supplied cost.
- **R-13** Unlike R-10, an `OPERATOR` MUST be allowed (`docs/casos_de_uso.md` §2.3, "Registrar
  mermas y daños").

### Minimum-stock threshold (CU-INV-07)

- **R-14** Settable per product in the caller's own branch only, to a value `>= 0`; *given* a
  negative value, *then* refused. Setting it MUST NOT write a Kardex row — no balance changes.
- **R-15** *Given* a threshold committed above the current stock, *then* the breach is evaluated and
  the `STOCK_MINIMUM` alert raised `AFTER_COMMIT`, exactly as a movement would (RF-INV-07).

### Kardex history (CU-INV-08)

- **R-16** Queryable by product and date range, ordered chronologically, each row exposing type,
  quantity, unit cost, `previous_stock`, `resulting_stock`, reason, responsible user and UTC
  timestamp (HU-INV-02).
- **R-17** The module MUST expose **no** update or delete path for `kardex_movements`
  (RN-12, RNF-INT-02) — append-only as a property of the code, not only of a convention.
- **R-18** *Given* the full Kardex of a product in a branch replayed from `INITIAL_LOAD`, *then* the
  sum MUST equal `branch_inventories.current_stock` exactly (HU-INV-02). This is what the atomicity
  IT proves.
- **R-19** *Given* a `BRANCH_MANAGER` of branch A requesting a movement of branch B, *then* the
  system responds as if it did not exist.

### Alerts (CU-ALE-01, CU-ALE-02)

- **R-20** *Given* a committed movement leaving `current_stock <= min_stock_threshold`, *then* a
  `STOCK_MINIMUM` alert MUST exist for that branch and product, with severity `CRITICAL` when the
  resulting stock is zero and `WARNING` otherwise (HU-DSH-02).
- **R-21** *Given* an unresolved alert with the same branch, type and subject token (F-1), *when*
  the condition persists across further movements, *then* no second row MUST be created (HU-ALE-01).
- **R-22** *Given* a movement lifting stock back above the threshold, *then* the alert MUST NOT be
  auto-resolved — resolution is an explicit human act (CU-ALE-02).
- **R-23** Resolution MUST set `is_resolved`, `resolved_at` and `resolved_by_user_id` atomically;
  *given* an already-resolved alert, *then* refused.
- **R-24** Listing MUST be scoped to the caller's branch (HU-ALE-02), ordered by severity then
  recency. Alert persistence failure MUST NOT roll back the movement that triggered it (P-10).

---

## 5. Authorization matrix

Cross-checked against `docs/casos_de_uso.md` §2.3. Enforced with `hasAuthority()` — never
`hasRole()`, which prepends `ROLE_`.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | Branch scoping |
| :--- | :---: | :---: | :---: | :--- |
| Read own-branch stock (CU-INV-03) | ✅ | ✅ | ✅ | session-derived; `ADMIN` has no branch → `branch_context_required` |
| Read network availability (CU-INV-04) | ✅ | ✅ | ✅ | all active branches, read-only (RN-08) |
| Register adjustment (CU-INV-05) | ✅\* | ✅ | ❌ | session-derived (RN-14) |
| Register write-off (CU-INV-06) | ✅\* | ✅ | ✅ | session-derived (RN-14) |
| Set minimum threshold (CU-INV-07) | ✅\* | ✅ | ❌ | session-derived (RN-14) |
| Read Kardex (CU-INV-08) | ✅ | ✅ | ❌ | own branch; `ADMIN` reads any branch |
| List / resolve alerts (CU-ALE-02) | ✅ | ✅ | ❌ | own branch; `ADMIN` reads any branch |

**\* Conflict, stated rather than softened.** §2.3 grants `ADMIN` "mutar stock de otra sucursal",
but RN-14 forbids the branch ever arriving as a client parameter and a corporate `ADMIN` has
`branch_id = NULL` (`AuthenticatedPrincipal.isCorporate()`). Letting `ADMIN` pass a branch would
put a branch parameter on a mutating endpoint — the exact shape RN-14 exists to forbid.
**Resolution: no mutating endpoint here accepts a branch; a corporate `ADMIN` invoking one gets
`403 branch_context_required`.** That capability stays reachable through `StockMutationPort` for
later modules, and through a future explicitly-scoped admin endpoint if a UI ever needs it.

`OPERATOR` is denied the Kardex and the alert centre because CU-INV-08 and CU-ALE-02 both name
*Gerente de Sucursal* as principal actor and §2.3's nearest analogue (audit log) denies `OPERATOR`.

---

## 6. API surface

All identifiers are `external_id` UUIDs (RNF-API-02). Page envelope matches the existing catalog
controllers: `{ content, totalElements, page, size }`. Errors use the uniform `{ code, message }`
envelope. Paths follow the `/api/<module>/…` convention already set by `iam` and `catalog`.

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/inventory/stock` | CU-INV-03 | `productExternalId?`, `belowThreshold?`, `page`, `size`, `sort` (`product`, `currentStock`) | page of `{ productExternalId, sku, name, currentStock, reservedStock, inTransitStock, availableStock, minStockThreshold, averageCost, lastUpdatedAt }` |
| `GET` | `/api/inventory/stock/{productExternalId}/network` | CU-INV-04 | — | `{ productExternalId, sku, name, branches: [{ branchExternalId, branchName, currentStock, reservedStock, inTransitStock, availableStock, isOwnBranch }], networkTotal }` |
| `POST` | `/api/inventory/adjustments` | CU-INV-05 | `{ productExternalId, countedQuantity, reason }` | `201` `{ movementExternalId, movementType, quantity, previousStock, resultingStock, createdAt }` |
| `POST` | `/api/inventory/write-offs` | CU-INV-06 | `{ productExternalId, quantity, reason }` | `201`, same movement shape |
| `PUT` | `/api/inventory/stock/{productExternalId}/threshold` | CU-INV-07 | `{ minStockThreshold }` | `200` `{ productExternalId, minStockThreshold }` |
| `GET` | `/api/inventory/kardex` | CU-INV-08 | `productExternalId?`, `movementType?`, `from?`, `to?`, `page`, `size`; ordered `created_at` ascending | page of `{ externalId, productExternalId, movementType, quantity, unitCost, totalCost, previousStock, resultingStock, referenceType, referenceId, notes, userExternalId, createdAt }` |
| `GET` | `/api/notifications/alerts` | CU-ALE-02 | `resolved?`, `alertType?`, `severity?`, `page`, `size`; ordered severity then `created_at` descending | page of `{ externalId, alertType, severity, title, message, isResolved, resolvedAt, createdAt }` |
| `PATCH` | `/api/notifications/alerts/{externalId}/resolve` | CU-ALE-02 | — | `200` resolved alert |

No endpoint, in path, query or body, accepts `branchId` (RN-14). Quantities are decimals in the
product's base unit (RN-13). Maximum page size is enforced server-side (RNF-PER-04).

---

## 7. Error taxonomy

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` | 400 | bean-validation failure, malformed UUID, page size above cap, malformed date range |
| `adjustment_reason_required` | 400 | R-07 — blank or absent reason on an adjustment or write-off |
| `adjustment_without_difference` | 400 | R-08 — counted quantity equals current balance |
| `branch_context_required` | 403 | §5 — corporate `ADMIN` invoking a session-scoped mutation |
| `cross_branch_access_denied` | 403 | a mutation resolved to a branch other than the session's (defence in depth behind RN-14) |
| `product_not_found` | 404 | the product `external_id` names nothing in `catalog` |
| `inventory_record_not_found` | 404 | no `branch_inventories` row and the operation cannot create one |
| `alert_not_found` | 404 | unknown alert **or** an alert of another branch (R-19, R-24) |
| `insufficient_stock` | 409 | R-11 / RN-01 — the movement would drive `current_stock` below zero |
| `alert_already_resolved` | 409 | R-23 |
| `concurrent_stock_update` | 409 | the pessimistic lock could not be acquired within the timeout |

**Must not leak.** Whether a resource exists in another branch (`404`, never `403`, per R-19 and
R-24); numeric `id` values, in any field, message or `Location` header; stack traces, SQL text,
constraint names or JPA exception messages; another branch's balances beyond the read explicitly
authorized by CU-INV-04.

---

## 8. Transactional and consistency guarantees

- **T-01** Atomic together, in one transaction, always: the `branch_inventories` update, the
  `kardex_movements` insert, and the `audit_logs` entry (RN-02, RNF-INT-01, RF-VAL-02).
- **T-02** Pessimistic lock (`SELECT … FOR UPDATE`) on the `branch_inventories` row is REQUIRED
  before reading the balance that the mutation derives from (CU-INV-05 step 5, F-2). Two concurrent
  write-offs of the same product MUST serialize, and the loser MUST see the winner's balance.
- **T-03** `audit_logs.branch_id` is the branch of the **mutated resource**, not of the actor.
- **T-04** `AFTER_COMMIT` only, in its own transaction: the `STOCK_MINIMUM` alert (P-08, P-10).
  Nothing in `notifications` may be reachable from inside a stock transaction.
- **T-05** Reads (CU-INV-03, CU-INV-04, CU-INV-08, alert listing) are `readOnly` and take no lock
  (RN-09).
- **T-06** Idempotency: alert creation is idempotent by the F-1 dedup key (R-21); alert resolution
  is idempotent-by-refusal (R-23). Adjustments and write-offs are **not** idempotent — each call is
  a distinct audited event, and no de-duplication key is introduced.
- **T-07** The schema `CHECK (current_stock >= 0)` is the last line of defence (RNF-INT-03), never
  the first: domain validation MUST reject the operation before the insert.

---

## 9. Non-functional obligations

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-01 | p95 < 200 ms for CU-INV-03, CU-INV-04, CU-INV-08 | index-backed queries; `idx_branch_inventory_critical` and `idx_kardex_branch_product` MUST be the access paths — no full scan, no N+1 |
| RNF-PER-02 | < 500 ms for the atomic mutation | one locked row, one insert, one audit insert; no remote call inside the transaction |
| RNF-PER-03 | cross-branch read must not degrade local transactions | T-05: read-only, unlocked |
| RNF-PER-04 | every collection paginated with a server-side max page size | `invalid_request` above the cap |
| RNF-INT-02 | Kardex append-only | R-17: no update/delete path exists in code |
| RNF-SEC-03 | branch isolation | R-01, R-19, R-24 and §5 |
| RNF-SEC-05 | all input validated in the backend | bean validation plus domain value objects |
| RNF-API-01 | OpenAPI documents each endpoint, its statuses and its error envelope | `/v3/api-docs` contains all eight operations |
| RNF-OBS-01 | structured logs carry correlation id, user, branch, operation; never credentials | alert-listener failures logged (P-10) |

---

## 10. Definition of done

Verifiable in the three planned PRs.

**PR 1 — domain + application**

- [ ] `com.optiplant.inventory.inventory.domain` and `…notifications.domain` exist with no Spring
      or Jakarta import.
- [ ] `shared/stock/StockMutationPort` and the `shared` alert event exist; `shared` still imports no
      module name.
- [ ] Unit `*Test` (no Docker) covering: R-06 sign derivation, R-07, R-08, R-11 insufficient
      stock, R-12 valuation at `average_cost`, R-14 negative threshold, P-03 cost presence rules,
      R-20 severity, R-21 dedup key.
- [ ] `cd backend && ./mvnw verify` green.

**PR 2 — infrastructure + web**

- [ ] Adapters, controllers, exception handler and `SecurityConfig` matchers for
      `/api/inventory/**` and `/api/notifications/**`, using `hasAuthority()`.
- [ ] `inventory` implements `ProductStockPresencePort`; `catalog`'s base-unit change stops
      answering `UNKNOWN`.
- [ ] Every §7 code is reachable from at least one controller path — no dead error code.
- [ ] `./scripts/validar_esquema.sh` green — expected to be **unaffected**, since no
      `backend/init-db/` file changes (§2.5). If it must change, §2.5 was wrong: stop and report.
- [ ] `cd backend && ./mvnw verify` green.

**PR 3 — verification**

Testcontainers `*IT` are reserved for the invariants that can break the system. CRUD and read paths
are covered by unit tests plus one smoke assertion per endpoint.

- [ ] `KardexAtomicityIT` — R-18: replaying the Kardex equals `current_stock`; a forced failure
      after the balance update leaves **neither** the balance change nor the Kardex row (T-01).
- [ ] `BranchIsolationIT` — R-01, R-19, R-24: branch A's session cannot read or mutate branch B.
- [ ] `StockValidationIT` — R-11 / RN-01 under two concurrent write-offs (T-02): exactly one
      succeeds, stock never goes negative, no `500`.
- [ ] `StockAlertIT` — R-20, R-21, R-24: the alert exists after commit, is not duplicated, and its
      failure does not roll back the movement.
- [ ] Smoke coverage of the four read endpoints and the threshold endpoint (status, envelope shape,
      no numeric id in the payload).
- [ ] `python3 scripts/validar_trazabilidad.py` green (§3: no `docs/` edit expected).
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

---

## 11. Open questions

None blocking. Four decisions were taken here rather than escalated, each recorded with its
reversal cost:

- **PA-01 — `OPERATOR` denied the Kardex and the alert centre.** §2.3 has no explicit row; CU-INV-08
  and CU-ALE-02 name *Gerente de Sucursal*. Cheap to reverse: one `SecurityConfig` matcher.
- **PA-02 — corporate `ADMIN` cannot invoke session-scoped mutations (§5).** Preserves RN-14.
  Reversing means adding a branch-carrying admin endpoint — additive, no rework of what ships here.
- **PA-03 — alerts are never auto-resolved (R-22).** Reversing is one listener, additive.
- **PA-04 — the F-1 dedup token lives in `system_alerts.title`.** The only no-migration option.
  Reversing costs a schema change to add `product_id`, which is precisely what this change refuses
  to do three days before delivery.
