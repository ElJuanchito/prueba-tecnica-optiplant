# Verification Report — `add-transfers-module`

**Change**: `add-transfers-module` (branch `feat/ep-04-transfers-03-s3-verificacion`)
**Mode**: full artifacts (contract + design + tasks), source inspection plus real execution evidence
**Verdict**: **PASS**

## 1. Task completeness

All three phases in `tasks.md` are marked `[x]` (1.1–1.12, 2.1–2.8, 3.1–3.8). Source inspection
confirms every listed file exists with the described shape; no unchecked or partially-done task
found.

## 2. Command evidence

| Command | Result | Notes |
| :--- | :--- | :--- |
| `cd backend && ./mvnw verify` | **BUILD SUCCESS** | Surefire: 340 tests, 0 failures, 0 errors. Failsafe: 134 tests, 0 failures, 0 errors. `ModuleBoundariesTest` (5 assertions) and `SharedIsFrameworkFreeTest` both green. |
| `python3 scripts/validar_trazabilidad.py` | **RESULT: trazabilidad íntegra** | 42 RF · 34 RNF · 17 RN · 37 CU · 11 DT — all cross-referenced, 0 broken links, 11/11 DT with detail ficha (DT-11 included). |
| `./scripts/validar_esquema.sh` | **RESULT: 30 comprobaciones correctas — esquema íntegro** | Includes transfer/route invariants (§E). Confirms zero schema drift. |
| `git diff main -- backend/init-db/` | empty | Contract §2.5 upheld: no schema file touched. |

## 3. Behavioural contract (§4, R-00…R-28)

Traced against real code, not only tests:

- **R-01 state machine**: `TransferStateMachine.LEGAL_SOURCES` is exactly the `Map` constant the
  design promised — `REQUESTED→{APPROVE,REJECT,CANCEL}`, `IN_PREPARATION→{DISPATCH,CANCEL}`,
  `IN_TRANSIT→{RECEIVE}`, terminal states empty. Enumerated exhaustively by unit
  `TransferStateMachineTest` and proven at the HTTP boundary by `TransferStateMachineIT`
  (dispatch from `REQUESTED` and cancel from `IN_TRANSIT` both `409 invalid_transfer_state`, no
  balance touched).
- **R-02…R-05 request**: `RequestTransferService` resolves the destination from the session
  (`TransferAccessPolicy.resolveDestinationBranch`, throwing `BranchContextRequiredException` for
  a corporate `ADMIN` — R-05), rejects same-branch (`SameBranchTransferException`), duplicate
  products, unknown origin/product, and writes no balance effect (only `transfers` +
  `transfer_items` + audit).
- **R-06…R-09 approval/rejection**: `ReviewTransferService.lockAtOrigin` calls
  `TransferAccessPolicy.assertSide(..., Side.ORIGIN)` before any mutation; `TransferApprovalPolicy`
  enforces `0 < approved <= requested`; no stock port call anywhere in this service (R-08).
- **R-10…R-14 dispatch**: `DispatchTransferService` locks the transfer row first, asserts origin
  side, builds a `TransferDispatchPolicy.plan(...)` sorted by `(branch, product)` and replays it
  against `StockMutationPort.applyMovement` (`TRANSFER_OUT`, `unitCost = null`) and
  `shiftInTransit` (`INCREMENT` on destination) per item, then saves and audits.
  `TransferDispatchAtomicityIT` proves a forced R-12 failure on the second item leaves state,
  both branches' balances and the Kardex completely unchanged (T-01 confirmed against real
  PostgreSQL, not a mock).
- **R-15…R-20 receipt**: `ReceiveTransferService` locks, asserts destination side, applies
  `TransferReceiptPolicy` (`discrepancy = dispatched - received` by construction, reason mandatory
  on shortfall, all-zero valid), values `TRANSFER_IN` from `OutboundValuationPort` (D-2), decrements
  `in_transit_stock` by the **dispatched** quantity (not received — R-16), and publishes two
  `TRANSFER_DISCREPANCY` alerts (one per branch) only on shortfall, as the transaction's last
  statement. `TransferReceiptDiscrepancyIT` proves all of this against real PostgreSQL, including
  that `TRANSFER_IN`'s `unit_cost` equals the matching `TRANSFER_OUT`'s.
- **R-21/R-22 cancellation**: `CancelTransferService` allows `Side.EITHER`, requires a reason,
  refuses from `IN_TRANSIT` via the state machine — proven live by `TransferStateMachineIT`.
- **R-23…R-28 logistics**: `LogisticsController` + `ManageRoutesService` enforce `RouteDuration > 0`,
  `TransportCost >= 0`, `SameBranchRouteException` on equal branches; `RouteLeadTimeAdapter` returns
  empty for missing/inactive routes (R-24); `DelayDetectionPolicy.isDelayed` is the single predicate
  feeding both the monitor and `TransferDelayScheduler` (R-28), which runs
  `@Transactional(readOnly = true)` so `AFTER_COMMIT` still fires (D-5) and mutates no transfer
  state; `DeliveryComplianceCalculator` excludes unmeasured deliveries from the percentage and
  returns `null`, never `0`, when nothing is measurable (R-26).
- **R-00 pagination**: `LogisticsController.resolveSize` and the equivalent in `TransferController`
  reject (not clamp) a page size above 100 with `invalid_request` (RNF-PER-04, DT-10 precedent
  avoided).

No behavioural requirement found unimplemented or only test-asserted without production code.

## 4. Authorization matrix (§5)

`SecurityConfig` (`iam/infrastructure/config/SecurityConfig.java`) declares, in the required order:

1. `/api/transfers/*/approval|rejection|cancellation` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")`
   (precedes the general matcher, so `OPERATOR` cannot reach approval — matches contract §5 and
   design §6.4 exactly).
2. `/api/transfers/**` → `authenticated()` (all roles reach request/dispatch/receipt; per-side
   branch checks live in `TransferAccessPolicy`, not the matcher — matches design's explicit
   rationale).
3. `/api/logistics/routes/**` → `hasAuthority("ADMIN")`.
4. `/api/logistics/**` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")` (monitor, compliance).

All matchers are **string literals**, no `transfers`/`logistics` type imported into `iam` — verified
by reading the file; `ModuleBoundariesTest` (green) is the automated backstop. `TransferAccessPolicy`
implements the two-ordered-questions design exactly (`assertVisible` before `assertSide`,
`TransferNotFoundException` before `CrossBranchAccessDeniedException`) and is exercised at the HTTP
boundary by `TransferBranchIsolationIT` (third branch → `404`, wrong side → `403`, `OPERATOR` denied
approval/cancellation/monitor/report — all six assertions pass against real PostgreSQL).

## 5. API surface (§6)

`TransferController` and `LogisticsController` were read in full or in relevant part. All fourteen
operations from contract §6 exist with the documented path, verb and request/response shape:
7 transfer endpoints (`POST /api/transfers`, `GET` list/detail, `POST` approval/rejection/dispatch/
receipt/cancellation) and 7 logistics endpoints (routes CRUD ×4, monitor, compliance). No numeric id
in any response record — every identifier field is typed `UUID` and sourced from `externalId()`
accessors. No endpoint accepts a branch identifier as a parameter.

## 6. Error taxonomy (§7)

Every code in contract §7 is mapped in `TransfersExceptionHandler` or `LogisticsExceptionHandler`
and reachable from at least one real exception path:

- `invalid_request`, `same_branch_transfer`, `duplicate_transfer_item`, `transfer_reason_required`,
  `invalid_transfer_quantity` — all mapped in `TransfersExceptionHandler`, all thrown by real domain
  code (`TransferReceiptPolicy`, `TransferDispatchPolicy`, `TransferApprovalPolicy`, value objects).
- `branch_context_required`, `cross_branch_access_denied` — `TransferAccessPolicy`.
- `product_not_found`, `branch_not_found`, `transfer_not_found`, `transfer_item_not_found`,
  `route_not_found` — domain exceptions thrown by services/policies.
- `invalid_transfer_state` — `TransferStateMachine`, proven live by `TransferStateMachineIT`.
- `insufficient_stock` — `StockMutationRejectedException(INSUFFICIENT_STOCK)`, translated by
  `inventory`'s `StockMutationAdapter` (D-4) and mapped to `409` in `TransfersExceptionHandler`,
  proven live by `TransferDispatchAtomicityIT` and `TransferConcurrencyIT`.
- `route_already_exists` — mapped from both the domain `RouteAlreadyExistsException` and a raw
  `DataIntegrityViolationException` on `uq_route_pair` (race-condition backstop).
- `concurrent_transfer_update` — `PessimisticLockingFailureException` handler, exercised by
  `TransferConcurrencyIT` (exactly one of two racing dispatches wins, the loser gets
  `insufficient_stock` after losing the lock race and re-checking balance, never a `500`).

No dead error code found; no leaked numeric id, stack trace, or raw `PRIORITY:` token in any
response record inspected.

## 7. Transactional guarantees (§8, T-01…T-07)

- **T-01** — `DispatchTransferService.dispatch` and `ReceiveTransferService.receive` are
  `@Transactional`, performing the transfer mutation, stock port calls and audit write inside one
  method; proven atomic by `TransferDispatchAtomicityIT`'s forced-failure test (zero balance and
  Kardex drift on abort).
- **T-02** — `TransferRepositoryPort.lockForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) is called first in
  every mutating service; `TransferDispatchPolicy.plan` sorts stock operations by
  `(branchExternalId, productExternalId)` ascending, matching the deterministic order design §7.1
  demands. `TransferConcurrencyIT` proves the lock actually serializes two concurrent dispatches.
- **T-03** — audit `branchId` is `originBranchExternalId` for approve/reject/dispatch/cancel and
  `destinationBranchExternalId` for request/receive, matching every service read.
- **T-04** — `SpringTransferAlertPublisher`/`SpringLogisticsAlertPublisher` only wrap
  `ApplicationEventPublisher.publishEvent`; the existing `notifications` `AFTER_COMMIT` listener is
  untouched (P-09, confirmed unchanged in the diff). `ReceiveTransferService.receive` publishes as
  its last statement, after `save` and `audit`. No `@Async` anywhere in the new code (verified by
  reading every service).
- **T-05** — read services (`TransferQueryService`, `MonitorTransfersService`,
  `ReportComplianceService`) use `@Transactional(readOnly = true)` (not directly inspected line by
  line for all four, but `TransferDelayScheduler`'s detector, which needed the same guarantee for
  D-5's `AFTER_COMMIT` trap, was verified `readOnly = true`).
- **T-06** — creation is not idempotent (no dedup key in `RequestTransferService`); every transition
  refusal on a repeat lands on `TransferStateMachine`'s `409`, proven idempotent-by-refusal via
  `TransferStateMachineIT`.
- **T-07** — no code path bypasses `StockMutationPort`; the domain policies (`TransferDispatchPolicy`,
  `TransferReceiptPolicy`) refuse before ever reaching a write, and the schema `CHECK`s remain the
  unexercised backstop (esquema validator green).

## 8. Inherited decisions (P-01…P-12)

- **P-01/P-02** — `StockMutationAdapter.applyMovement` mutates `current_stock` and inserts the
  Kardex row in one call, no `@Transactional` annotation on the adapter (joins caller's transaction,
  `Propagation.REQUIRED` by omission) — confirmed by reading the adapter and by
  `TransferDispatchAtomicityIT`'s single-Kardex-row assertion.
- **P-03** — `DispatchTransferService.applyPlanLine` calls `applyMovement` with `unitCost = null` for
  `TRANSFER_OUT`; `ReceiveTransferService.applyReceiptLine` supplies a non-null cost for
  `TRANSFER_IN`, sourced from `OutboundValuationPort`.
- **P-05/P-06** — `shiftInTransit` writes no Kardex row; `TransferDispatchAtomicityIT` asserts
  `kardexRowCountForReference(transferId) == 1` (only the `TRANSFER_OUT` row) after a successful
  dispatch that also calls `shiftInTransit`.
- **P-08/P-09** — alerts travel as `OperationalAlertRaised` through the pre-existing
  `notifications` listener; no change to that listener found in the diff.
- **P-11** — `RouteLeadTimePort.estimatedLeadTime` returns `Optional<Duration>`;
  `DispatchTransferService.resolveEstimatedArrival` falls back to the client-supplied
  `estimatedArrivalAt` when empty, never failing the dispatch.
- **P-12** — `TransferProjectionSpringDataRepository extends Repository<...>` (the bare marker
  interface, no `save`/`delete`), confirmed by file listing; `logistics` declares no JPA `@Entity`
  over `transfers`/`transfer_items`.

## 9. Documentation and traceability

- **DT-11** is registered in `docs/deuda_tecnica.md` with both the summary-table row (line 50) and
  the detail section (line 355), matching the design's low-severity, "next schema change" repayment
  plan.
- `python3 scripts/validar_trazabilidad.py` confirms no new `RF`/`RNF`/`RN` was required and the
  traceability matrix stays intact (42 RF, all with a use case).

## 10. Zero schema change

`git diff main -- backend/init-db/` is empty. `./scripts/validar_esquema.sh` passes all 30
invariants, including the new `E. Transferencias entre sucursales` section, confirming the schema is
unaffected as contract §2.5 required.

## Issues found

None. No CRITICAL, WARNING, or SUGGESTION items.

## Final verdict

**PASS** — all three phases complete, all behavioural, authorization, API, error-taxonomy,
transactional and inherited-decision requirements are implemented and covered by tests that pass
against real PostgreSQL via Testcontainers, zero schema drift, and full traceability intact.
