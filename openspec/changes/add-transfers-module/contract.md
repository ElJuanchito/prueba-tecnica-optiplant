# Contract — `add-transfers-module`

Acceptance contract for the `transfers` and `logistics` module packages.
Step 1 of 3: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §4
(RN-01 … RN-17), `docs/casos_de_uso.md` §2.3 and §3.5–§3.6, and
`docs/decisiones_arquitectura_tecnica.md` §2.3–§2.4 alongside this document.

---

## 1. Scope

Two module packages in one change: **`transfers`** (the five-state inter-branch transfer machine —
request, approval, dispatch, receipt, cancellation) and **`logistics`** (route parameterization,
active-transfer monitoring, on-time compliance reporting, scheduled delay detection). Two modules
with independently verified ArchUnit boundaries, not one fused module.
`docs/decisiones_arquitectura_tecnica.md` §2.4 is unchanged.

`transfers` is the first real consumer of `shared/stock/StockMutationPort`; this change proves the
port that `add-inventory-module` declared and never exercised.

**Out of scope**

- Any schema change: the three tables already ship in `01-init-schema.sql` (§2.5).
- Writing `branch_inventories` or `kardex_movements` directly — every balance effect goes through
  `StockMutationPort` (§2.2). Neither module owns an inventory table.
- Alert persistence and the alert centre (`notifications` owns them); these modules only publish.
- Discrepancy follow-up as system state: CU-TRA-05 FA-01 is a new request, FA-02 is CU-INV-06,
  FA-03 is a note. No claim entity is introduced.
- Dashboards over this data (CU-DSH-03, `analytics`) and the frontend.

---

## 2. Affected modules

### 2.1. Dependency directions

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `transfers` | `shared` | `AuthenticatedPrincipal`, `Role`, `StockMutationPort`, `AuditWritePort`, `OperationalAlertRaised` | one-way |
| `logistics` | `shared` | `AuthenticatedPrincipal`, `Role`, `AuditWritePort`, `OperationalAlertRaised` | one-way |
| `transfers` → `logistics` | — | new `shared` route lead-time port (§2.3) | no direct edge |
| `transfers` / `logistics` → `notifications` | — | `AFTER_COMMIT` event on `shared` | no compile-time edge |

Every cross-module contact is a `shared` type, so the graph stays acyclic.
`ModuleBoundariesTest.MODULOS` already lists both names (`ModuleBoundariesTest.java:36-37`) — no
new rule needed. Foreign keys to `products`, `branches` and `users` are resolved by each module's
own persistence adapter with native `external_id → id` queries, exactly as
`inventory/…/ForeignKeyResolverSpringDataRepository.java` already does: no lookup port, no new edge.

### 2.2. Inherited decisions — not reopened

From `openspec/changes/archive/2026-08-29-add-inventory-module/contract.md` §2.2–§2.4:

- **P-01/P-02** `StockMutationPort.applyMovement` mutates `current_stock` and inserts the Kardex row in the caller's transaction (RN-02, RNF-INT-01). Never `@Async`, never `AFTER_COMMIT`.
- **P-03** `TRANSFER_OUT` is outbound (`inventory` stamps `average_cost`, RN-03); `TRANSFER_IN` is inbound and requires a unit cost — supplied from the dispatch valuation, never by the client.
- **P-05/P-06** `StockMutationPort.shiftInTransit` moves the **destination** row's `in_transit_stock` and MUST NOT write a Kardex row (RN-04). A named port call, not a side effect.
- **P-08/P-09** Alerts travel as `OperationalAlertRaised` published `AFTER_COMMIT`. `TRANSFER_DISCREPANCY` and `LOGISTIC_DELAY` already exist in `shared/alert/AlertType`; the `notifications` listener needs no change.

### 2.3. Decision — route lead time crosses as a port, monitoring does not

- **P-11** `logistics` MUST expose a synchronous `shared` port answering the estimated duration for an ordered branch pair; `transfers` calls it at dispatch to precompute `estimated_arrival_at` (HU-LOG-03). A missing or inactive route MUST return empty, never fail the dispatch: the operator's ETA then stands, and with neither the field stays `NULL` and the transfer is excluded from punctuality metrics (R-26).
- **P-12** In the opposite direction `logistics` reads transfer rows through its **own read-only projections**, not a port — reporting aggregates are not a question one port answers. **`transfers` is the only writer of `transfers` and `transfer_items`**; `logistics` MUST issue no `INSERT`/`UPDATE`/`DELETE` against them. ArchUnit cannot see SQL, so this is a review obligation.

### 2.4. Decision — one receipt endpoint, two outcomes

CU-TRA-04 and CU-TRA-05 are one physical act with a comparison in the middle (CU-TRA-05 EX-02 routes
back to CU-TRA-04). One receipt operation takes the received quantities and resolves to `RECEIVED` or
`RECEIVED_WITH_DISCREPANCY`; splitting them would let the client pick the outcome, which is exactly
what RN-06 forbids being negotiable.

### 2.5. Schema findings — reported, not migrated

| # | Finding | Resolution inside the existing schema |
| :--- | :--- | :--- |
| **F-1** | `transfers` has **no priority column**, yet RF-TRA-01 and HU-TRA-01 require stating urgency and sorting by it. | Deterministic first-line token in `transfers.notes`: `PRIORITY:<LOW\|STANDARD\|URGENT>`, human observations on later lines — the technique already used for `system_alerts.title`. The API exposes `priority` as a field and never leaks the token. |
| **F-2** | `transfer_items` has **no `approved_quantity`**, yet RF-TRA-02 allows partial approval. | Approval writes the approved value into `requested_quantity` (post-approval it is the agreed quantity). The prior value survives in `audit_logs.payload_before` (RF-VAL-02, RN-12) and the difference is appended to `notes` — the treatment CU-TRA-03 FA-01 already prescribes for the dispatch difference. |
| **F-3** | `transfers` has dispatcher and receiver columns but **no approver or canceller**. | Those actors are recorded in `audit_logs` (entity `TRANSFER`), which CU-SEG-04 already exposes. |
| **F-4** | `CHECK (discrepancy_quantity >= 0)` plus RN-06 make **over-receipt unrepresentable**, but CU-TRA-05 EX-01 contemplates it. | Over-receipt is refused (R-19); the excess enters destination stock as a separate `ADJUSTMENT_POS` via CU-INV-05, which already demands `BRANCH_MANAGER` (RN-11) and audits it — EX-01's intent, no migration. |
| **F-5** | `transfers` has **no version column**, so optimistic locking is unavailable. | Pessimistic row lock (`SELECT … FOR UPDATE`) on the transfer row for every transition (T-02). |
| **F-6** | `logistics_routes` has `uq_route_pair` on the ordered pair and **no `updated_at`**. | Routes are directional: A→B and B→A are two rows. Editing updates in place; removal is `is_active = FALSE`, never a delete. |

None of these blocks any of the nine use cases. **No `backend/init-db/` change is proposed.**

---

## 3. Traceability

Every identifier below was verified present in its source document.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| RF-TRA-01 | CU-TRA-01 | HU-TRA-01 |
| RF-TRA-02 | CU-TRA-02 | HU-TRA-02 |
| RF-TRA-03 | CU-TRA-03 | HU-TRA-03 |
| RF-TRA-04 | CU-TRA-04 | HU-TRA-04 |
| RF-TRA-05 | CU-TRA-05 | HU-TRA-05 |
| RF-TRA-06 | CU-TRA-06 | HU-TRA-06 |
| RF-LOG-01 | CU-LOG-02, CU-TRA-03 | HU-LOG-01 |
| RF-LOG-02, RF-LOG-04 | CU-LOG-03 | HU-LOG-02 |
| RF-LOG-03 | CU-LOG-01 | HU-LOG-03 |
| RF-VAL-01 | CU-TRA-05, CU-ALE-01 | HU-TRA-05, HU-ALE-01 |
| RF-VAL-02 | CU-TRA-02, CU-TRA-06 | HU-INV-04 |
| RF-INV-08 | traced by CU-TRA-03, CU-TRA-04, CU-TRA-05 | HU-INV-02 |
| RN-01 … RN-08, RN-12, RN-13, RN-14 | constrain the above | — |

**No new `RF` / `RNF` / `RN` is required**, so `docs/` needs no edit and the traceability matrix of
`docs/casos_de_uso.md` gains no row. `python3 scripts/validar_trazabilidad.py` — verified green
while writing this contract — must still pass unchanged.

---

## 4. Behavioural contract

**R-00** Every collection endpoint MUST paginate with a server-side maximum and MUST **reject** an oversized page with `400 invalid_request` — `inventory`'s pattern, not `catalog`'s silent clamp (`DT-10`). No endpoint accepts the acting branch in path, query or body (RN-14).

**R-01** `REQUESTED → IN_PREPARATION → IN_TRANSIT → RECEIVED | RECEIVED_WITH_DISCREPANCY` is mandatory and MUST NOT be skipped (RN-05); `CANCELLED` is reachable only from `REQUESTED` or `IN_PREPARATION` (RF-TRA-06). *Given* a transition whose source state is not the required one, *then* `409 invalid_transfer_state`, nothing written. Terminal states accept no transition.

### Request (CU-TRA-01)

- **R-02** MUST create a transfer in `REQUESTED` with a unique `transfer_number`, items with `requested_quantity > 0` in the base unit (RN-13) and a priority of `LOW|STANDARD|URGENT` (F-1). The **destination** is the session branch; the **origin** arrives as `originBranchExternalId` — RN-14 binds the *acting* branch to the session, the counterpart is a reference, never authority.
- **R-03** *Given* origin equal to destination, an unknown or inactive origin, an unknown or disabled product, or the same product twice, *then* refused with nothing written (HU-TRA-01).
- **R-04** Creating a request MUST NOT touch any balance — no reservation, no Kardex row (HU-TRA-01, fourth criterion).
- **R-05** *Given* a corporate `ADMIN` (`branchId == null`), *then* `403 branch_context_required`: there is no destination to derive (§5).

### Approval, adjustment, rejection (CU-TRA-02)

- **R-06** Invocable only by `BRANCH_MANAGER`/`ADMIN` of the **origin**; *given* any other branch, *then* `403 cross_branch_access_denied` (HU-TRA-02).
- **R-07** Approval MAY reduce an item to a value `> 0`, never raise it above the requested amount. *Given* 100 requested and 60 approved, *then* the agreed quantity is 60, the pair survives in the audit entry, the difference is appended as an observation (F-2), and the state becomes `IN_PREPARATION`.
- **R-08** Approval MUST NOT reserve or move stock; availability is revalidated at dispatch (CU-TRA-03 step 5).
- **R-09** Rejection moves the transfer to `CANCELLED` with a mandatory non-blank reason, alters no balance, and is audited (HU-TRA-02).

### Dispatch (CU-TRA-03)

- **R-10** Invocable only from the **origin**, only from `IN_PREPARATION`. MUST record carrier, tracking number, `dispatched_at`, `dispatched_by_user_id` and `estimated_arrival_at` (P-11).
- **R-11** Per item, in one transaction: `TRANSFER_OUT` on the origin through `applyMovement` (RN-02) plus an `INCREMENT` of the **destination**'s `in_transit_stock` through `shiftInTransit` (RN-04, P-05/P-06). `dispatched_quantity` is persisted per item.
- **R-12** *Given* insufficient origin stock at dispatch time, *then* `409 insufficient_stock`, the transfer stays `IN_PREPARATION`, and **no** item is partially dispatched — the dispatch is one atomic act (RN-01, CU-TRA-03 EX-01, HU-TRA-03 third criterion).
- **R-13** Dispatch MAY send less than agreed (`> 0`, never more); the difference is an observation (CU-TRA-03 FA-01), and the *dispatched* quantity is what in-transit, receipt and RN-06 operate on.
- **R-14** *Given* a dispatch attempted from `REQUESTED`, *then* refused by R-01 (HU-TRA-03, fifth criterion).

### Receipt, complete or partial (CU-TRA-04, CU-TRA-05)

- **R-15** Invocable only by the **destination**, only from `IN_TRANSIT`. MUST take a received quantity per dispatched item and persist `actual_arrival_at` and `received_by_user_id`.
- **R-16** Per item, in one transaction: `TRANSFER_IN` on the destination for the **received** quantity only, and a `DECREMENT` of `in_transit_stock` for the **full dispatched** quantity, so no phantom in-transit balance survives (CU-TRA-05 steps 6–7).
- **R-17** `received + discrepancy = dispatched`, per item, always (RN-06). *Given* 100 dispatched and 90 received, *then* discrepancy 10, destination stock +90, in-transit 0 (HU-TRA-05).
- **R-18** *Given* every item received in full, *then* `RECEIVED` and **no** alert. *Given* any shortfall, *then* `RECEIVED_WITH_DISCREPANCY`, a mandatory non-blank `discrepancy_reason` per short item, and a `TRANSFER_DISCREPANCY` alert of severity `CRITICAL` published `AFTER_COMMIT` for **both** branches — one event each, since `system_alerts.branch_id` holds one branch (RN-07).
- **R-19** *Given* a received quantity above the dispatched one, or negative, *then* refused (F-4). *Given* zero received on every item, *then* the receipt is still valid: total loss is a 100% discrepancy, not an error.
- **R-20** A received item MUST be valued at the unit cost captured on its `TRANSFER_OUT` movement, so the two branches do not diverge in valuation (RN-03, P-03).

### Cancellation (CU-TRA-06)

- **R-21** Allowed from `REQUESTED` and `IN_PREPARATION` only, by a `BRANCH_MANAGER`/`ADMIN` of **either** branch — the requester may withdraw, the origin may decline, and RF-TRA-06 names no single side. A mandatory reason is required and audited.
- **R-22** *Given* an `IN_TRANSIT` transfer, *then* `409 invalid_transfer_state`: goods in motion are resolved by receipt. *Given* a cancellation, *then* no balance changed anywhere (HU-TRA-06).

### Routes, monitoring, compliance (CU-LOG-01 … CU-LOG-03)

- **R-23** Routes are `ADMIN` only (`docs/casos_de_uso.md` §2.3), with duration `> 0`, cost `>= 0` and priority for an ordered pair; *given* equal branches or an existing pair, *then* refused (HU-LOG-03, F-6).
- **R-24** Deactivation is logical; an inactive route MUST NOT precompute an ETA (P-11) and MUST NOT break transfers already dispatched under it.
- **R-25** The monitor MUST list active transfers (`REQUESTED`, `IN_PREPARATION`, `IN_TRANSIT`) involving the caller's branch on either side, filterable by state, each carrying both branches, items, quantities and `estimated_arrival_at`, flagging as delayed any `IN_TRANSIT` transfer past its ETA (HU-LOG-01). `ADMIN` sees the network (RN-08).
- **R-26** The compliance report MUST return, for a date range, on-time percentage and average deviation in hours per route and per branch, from `actual_arrival_at` against `estimated_arrival_at` (RF-LOG-02, RF-LOG-04). Transfers with a null ETA MUST be excluded from the percentage and counted separately — never scored as on-time.
- **R-27** A transfer detail MUST expose estimated, actual and deviation in hours (HU-LOG-02).
- **R-28** The scheduled detector MUST publish one `LOGISTIC_DELAY` alert per involved branch for each `IN_TRANSIT` transfer past its ETA (CU-ALE-01, RF-VAL-01), keyed on the transfer `external_id` as subject token so `notifications` deduplicates it while the condition persists. It MUST mutate no transfer state and MUST NOT run inside a business transaction.

---

## 5. Authorization matrix

Cross-checked against `docs/casos_de_uso.md` §2.3. Enforced with `hasAuthority()` — never
`hasRole()`, which prepends `ROLE_`. "Own branch" is always the `AuthenticatedPrincipal` branch;
the counterpart is read from the persisted transfer, never from the request.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | Branch rule |
| :--- | :---: | :---: | :---: | :--- |
| Request (CU-TRA-01) | ✅\* | ✅ | ✅ | session branch is the **destination**; origin is a body reference |
| Approve / adjust (CU-TRA-02) | ✅ | ✅ | ❌ | actor's branch MUST equal the **origin** |
| Reject (CU-TRA-02) | ✅ | ✅ | ❌ | actor's branch MUST equal the **origin** |
| Dispatch (CU-TRA-03) | ✅ | ✅ | ✅ | actor's branch MUST equal the **origin** |
| Receive (CU-TRA-04, CU-TRA-05) | ✅ | ✅ | ✅ | actor's branch MUST equal the **destination** |
| Cancel (CU-TRA-06) | ✅ | ✅ | ❌ | actor's branch MUST be origin **or** destination (R-21) |
| List / read transfers | ✅ | ✅ | ✅ | own branch on either side; `ADMIN` network-wide |
| Routes CRUD (CU-LOG-01) | ✅ | ❌ | ❌ | corporate, no branch scope |
| Monitor (CU-LOG-02) | ✅ | ✅ | ❌ | own branch on either side; `ADMIN` network-wide |
| Compliance (CU-LOG-03) | ✅ | ✅ | ❌ | own branch on either side; `ADMIN` network-wide |

**\*** A corporate `ADMIN` has no destination to derive, so CU-TRA-01 answers
`403 branch_context_required` — the resolution the inventory contract already took for
session-scoped mutations, preserving RN-14 instead of reintroducing a client-supplied acting
branch. For the transitions the acting branch is the transfer's stored origin or destination, so
`ADMIN` needs no branch context and `AuthenticatedPrincipal.mayMutateBranch` already grants it.

`OPERATOR` is denied approval, rejection, cancellation, the monitor and the report: §2.3 grants
"Aprobar / rechazar transferencias como origen" to managers only, and CU-LOG-02/CU-LOG-03 name
*Gerente de Sucursal* as principal actor. `OPERATOR` keeps request, dispatch and receipt, which
§2.3 grants explicitly.

---

## 6. API surface

All identifiers are `external_id` UUIDs (RNF-API-02). Page envelope matches the existing
controllers: `{ content, totalElements, page, size }`; errors use `{ code, message }`. Quantities
are decimals in the product's base unit (RN-13).

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/transfers` | CU-TRA-01 | `{ originBranchExternalId, priority, notes?, items: [{ productExternalId, requestedQuantity }] }` | `201` transfer detail |
| `GET` | `/api/transfers` | listing | `status?`, `direction?` (`INBOUND`/`OUTBOUND`), `from?`, `to?`, `page`, `size`, `sort` (`priority`, `createdAt`) | page of summaries |
| `GET` | `/api/transfers/{externalId}` | detail, R-27 | — | transfer detail |
| `POST` | `/api/transfers/{externalId}/approval` | CU-TRA-02 | `{ items: [{ itemExternalId, approvedQuantity }], notes? }` | `200` detail |
| `POST` | `/api/transfers/{externalId}/rejection` | CU-TRA-02 | `{ reason }` | `200` detail |
| `POST` | `/api/transfers/{externalId}/dispatch` | CU-TRA-03 | `{ carrierName, trackingNumber?, estimatedArrivalAt?, items: [{ itemExternalId, dispatchedQuantity }] }` | `200` detail |
| `POST` | `/api/transfers/{externalId}/receipt` | CU-TRA-04, CU-TRA-05 | `{ items: [{ itemExternalId, receivedQuantity, discrepancyReason? }] }` | `200` detail |
| `POST` | `/api/transfers/{externalId}/cancellation` | CU-TRA-06 | `{ reason }` | `200` detail |
| `POST` | `/api/logistics/routes` | CU-LOG-01 | `{ originBranchExternalId, destinationBranchExternalId, estimatedDurationHours, transportCost, priorityLevel }` | `201` route |
| `GET` | `/api/logistics/routes` | CU-LOG-01 | `active?`, `page`, `size` | page of routes |
| `PUT` | `/api/logistics/routes/{externalId}` | CU-LOG-01 | duration, cost, priority | `200` route |
| `PATCH` | `/api/logistics/routes/{externalId}/deactivation` | R-24 | — | `200` route |
| `GET` | `/api/logistics/transfers/active` | CU-LOG-02 | `status?`, `delayed?`, `page`, `size` | page of `{ transferExternalId, transferNumber, status, originBranch, destinationBranch, priority, itemCount, totalQuantity, estimatedArrivalAt, isDelayed }` |
| `GET` | `/api/logistics/compliance` | CU-LOG-03 | `from`, `to`, `groupBy` (`ROUTE`/`BRANCH`), `page`, `size` | page of `{ key, label, deliveredCount, onTimeCount, onTimePercentage, averageDeviationHours, unmeasuredCount }` |

Transfer detail: `{ externalId, transferNumber, status, priority, originBranch: { externalId, name },
destinationBranch: { … }, carrierName, trackingNumber, dispatchedAt, estimatedArrivalAt,
actualArrivalAt, deviationHours, observations, requestedBy, dispatchedBy, receivedBy, createdAt,
updatedAt, items: [{ externalId, productExternalId, sku, name, requestedQuantity,
dispatchedQuantity, receivedQuantity, discrepancyQuantity, discrepancyReason }] }`. `observations`
is the human portion of `transfers.notes` with the F-1 token stripped. No numeric id appears
anywhere, and no endpoint accepts the acting branch (RN-14).

---

## 7. Error taxonomy

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` | 400 | bean-validation failure, malformed UUID, page size above cap, bad date range, unknown enum value |
| `same_branch_transfer` | 400 | R-03 — origin equals destination |
| `duplicate_transfer_item` | 400 | R-03 — the same product appears twice |
| `transfer_reason_required` | 400 | R-09, R-18, R-21 — blank rejection, discrepancy or cancellation reason |
| `invalid_transfer_quantity` | 400 | R-07, R-13, R-19 — approved above requested, dispatched above agreed, received above dispatched or negative |
| `branch_context_required` | 403 | §5 — corporate `ADMIN` requesting a transfer |
| `cross_branch_access_denied` | 403 | the actor's branch is not the side the transition requires (R-06, R-10, R-15, R-21) |
| `product_not_found` | 404 | a product `external_id` names nothing in `catalog`, or is disabled |
| `branch_not_found` | 404 | the origin branch `external_id` names nothing or is inactive |
| `transfer_not_found` | 404 | unknown transfer, **or** one involving neither of the caller's sides |
| `transfer_item_not_found` | 404 | an item `external_id` does not belong to this transfer |
| `route_not_found` | 404 | unknown route |
| `invalid_transfer_state` | 409 | R-01 — wrong source state, including cancelling in transit |
| `insufficient_stock` | 409 | R-12 / RN-01 — the dispatch would drive origin stock below zero |
| `route_already_exists` | 409 | R-23 — `uq_route_pair` violated for the ordered pair |
| `concurrent_transfer_update` | 409 | the pessimistic lock could not be acquired within the timeout (T-02) |

**Must not leak.** Whether a transfer or route exists between branches the caller is not part of
(`404`, never `403`); another branch's balances, valuations or thresholds beyond what the transfer
itself contains; numeric `id` values in any field, message or `Location` header; the raw F-1
priority token; stack traces, SQL text, constraint names or JPA exception messages.

---

## 8. Transactional and consistency guarantees

- **T-01** Atomic together per operation: the `transfers` state change, every `transfer_items` update, every `applyMovement` and `shiftInTransit` call, and the `audit_logs` entry (RN-02, RN-05, RF-VAL-02, RNF-INT-01). Dispatch and receipt are all-or-nothing across their items (R-12).
- **T-02** Pessimistic lock (`SELECT … FOR UPDATE`) REQUIRED on the `transfers` row before any transition (F-5) and, inside `StockMutationPort`, on each `branch_inventories` row touched. Rows MUST be locked in a deterministic order — transfer first, then inventory rows by branch then product — so two concurrent transfers between the same pair cannot deadlock.
- **T-03** `audit_logs.branch_id` is the branch of the **mutated resource**: origin for approval, rejection and dispatch; destination for receipt; the transfer's origin for cancellation.
- **T-04** `AFTER_COMMIT` only, in its own transaction: `TRANSFER_DISCREPANCY` (R-18) and `LOGISTIC_DELAY` (R-28). Their failure MUST be logged (RNF-OBS-01) and MUST NOT roll back the operation or surface to the caller.
- **T-05** Reads (listing, detail, monitor, compliance) are `readOnly` and take no lock (RN-09).
- **T-06** Every transition is idempotent-by-refusal: a repeat lands on a state R-01 rejects with `409`. Transfer creation is **not** idempotent and gains no deduplication key — two identical requests are two transfers. Delay alerts are idempotent through the `notifications` dedup key.
- **T-07** `CHECK (current_stock >= 0)` and `CHECK (discrepancy_quantity >= 0)` are the last line of defence (RNF-INT-03), never the first: the domain refuses before the write.

---

## 9. Non-functional obligations

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-01 | p95 < 200 ms for listing, detail, monitor, compliance | `idx_transfers_status`, `idx_transfers_origin`, `idx_transfers_destination`, `idx_transfer_items_transfer` MUST be the access paths; items fetched one query per page — no N+1 |
| RNF-PER-02 | < 500 ms for dispatch and receipt at 200 simultaneous active transfers (§5.1) | locked rows only; no remote call and no event dispatch inside the transaction |
| RNF-PER-04 | every collection paginated, oversized page rejected | `400 invalid_request` (R-00, `DT-10`) |
| RNF-INT-01 | dispatch and receipt atomic across items and branches | T-01, proven by `TransferDispatchAtomicityIT` |
| RNF-INT-02 | Kardex append-only | written only through `StockMutationPort`; no update or delete path exists |
| RNF-SEC-03 | branch isolation on both sides | §5, R-06, R-10, R-15; `transfer_not_found` over `403` |
| RNF-SEC-05 | all input validated in the backend | bean validation plus domain value objects, before any write |
| RNF-API-01 | OpenAPI documents each endpoint, its statuses and its error envelope | `/v3/api-docs` contains all fourteen operations |
| RNF-OBS-01 | structured logs carry correlation id, user, branch, operation | detector and alert-publication failures logged, never swallowed |
| RNF-MAN-01 | the state machine is covered by automated tests | §10 unit tests over every legal and illegal transition |

---

## 10. Definition of done

Verifiable in the three planned PRs (`openspec/PLAN.md` §3).

**PR 1 — domain + application**

- [ ] `com.optiplant.inventory.transfers.domain` and `…logistics.domain` exist with no Spring or Jakarta import; the `shared` route lead-time port (P-11) exists and `shared` still imports no module name.
- [ ] Unit `*Test` (no Docker) covering R-01 every legal and illegal transition, R-03, R-07, R-13, R-17 (RN-06 arithmetic), R-18 outcome and severity, R-19, R-21/R-22, R-23, and R-26's exclusion of unmeasured transfers.
- [ ] `cd backend && ./mvnw verify` green.

**PR 2 — infrastructure + web**

- [ ] Adapters, controllers, exception handler and `SecurityConfig` matchers for `/api/transfers/**` and `/api/logistics/**`, using `hasAuthority()`.
- [ ] `logistics` implements the lead-time port; `transfers` consumes it at dispatch; the scheduled delay detector (R-28) is registered and configurable.
- [ ] Every §7 code is reachable from at least one controller path — no dead error code.
- [ ] `./scripts/validar_esquema.sh` green — expected **unaffected**, since no `backend/init-db/` file changes (§2.5). If it must change, §2.5 was wrong: stop and report.
- [ ] `cd backend && ./mvnw verify` green.

**PR 3 — verification**

Testcontainers `*IT` are reserved for the invariants that can break the system.

- [ ] `TransferDispatchAtomicityIT` — R-11/R-12/T-01: `TRANSFER_OUT` on origin plus destination in-transit increment with **no** Kardex row for the shift; a forced mid-dispatch failure leaves state, balances and Kardex unchanged.
- [ ] `TransferReceiptDiscrepancyIT` — R-16/R-17/R-18: 100 dispatched, 90 received ⇒ +90 stock, discrepancy 10, in-transit 0, `RECEIVED_WITH_DISCREPANCY`, one alert per branch; a full receipt gives `RECEIVED` and no alert.
- [ ] `TransferStateMachineIT` — R-01/R-22: dispatch from `REQUESTED` and cancellation from `IN_TRANSIT` both rejected with no balance touched.
- [ ] `TransferBranchIsolationIT` — §5: a third branch sees `transfer_not_found`; the destination cannot dispatch and the origin cannot receive.
- [ ] `TransferConcurrencyIT` — R-12/T-02: two concurrent dispatches over the same stock, exactly one succeeds, stock never negative, no `500`.
- [ ] Smoke coverage of the read endpoints and route CRUD (status, envelope shape, no numeric id).
- [ ] `python3 scripts/validar_trazabilidad.py` green (§3: no `docs/` edit expected).
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

---

## 11. Open questions

None blocking. Six decisions were taken here rather than escalated, each with its reversal cost.

- **PA-01 — Priority persists as a token in `transfers.notes` (F-1).** RF-TRA-01 and HU-TRA-01 require it and the column does not exist; the alternative was an `ALTER`. Reversal: a migration adding `priority_level` plus a mapper change.
- **PA-02 — Approval overwrites `requested_quantity`; history lives in the audit log (F-2).** HU-TRA-02 asks the difference to be *documented*, which RF-VAL-02 already owns. Reversal: a migration adding `approved_quantity`.
- **PA-03 — Over-receipt is refused, not stored (F-4).** `CHECK (discrepancy_quantity >= 0)` plus RN-06 make it unrepresentable; CU-TRA-05 EX-01's intent is met by CU-INV-05. Reversal: a migration allowing a signed discrepancy.
- **PA-04 — One receipt endpoint resolves CU-TRA-04 and CU-TRA-05 (§2.4).** Reversal: splitting the path — additive, no domain rework.
- **PA-05 — `logistics` reads transfer rows directly; only `transfers` writes them (P-12).** A port per reporting query costs more than the coupling it removes, and `analytics` will read the same way. Reversal: a `shared` monitor port plus adapter — additive.
- **PA-06 — Cancellation is open to managers of both branches (R-21).** RF-TRA-06 and CU-TRA-06 name no single side and both have legitimate reason to abandon a request. Reversal: one authorization check.
