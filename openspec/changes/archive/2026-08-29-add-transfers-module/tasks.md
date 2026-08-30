# Tasks: `add-transfers-module`

Three phases, one PR each, matching `contract.md` §10 — the definition of done; `design.md` is the
shape, cited below by section. **Zero schema change**: if a task seems to need one, §2.5 was wrong —
stop and report. **The layer pattern is replicated, not invented** — `JpaEntity → Mapper →
SpringDataRepository → PersistenceAdapter → Controller → ExceptionHandler` already exists in
`inventory`; read the counterpart before writing each file.

## Phase 1 — S1: `transfers` and `logistics` domain and application (PR1)

- [x] 1.1 Create the three `shared` additions of design §2 — `route/RouteLeadTimePort` (P-11),
      `stock/OutboundValuationPort` (D-2), `stock/StockMutationRejectedException` with its `Reason`
      enum (D-4) — with Javadoc naming the last one the port's contractual failure mode, translated by
      the implementing adapter so other modules can map it.
- [x] 1.2 Create `transfers/domain/model/` value objects (§3.1) plus `TransferStatus`,
      `TransferTransition`, `TransferPriority` and `TransferNotes` (§3.5), whose `parse` MUST default
      to `STANDARD` on notes with no `PRIORITY:` line — `02-seed-data.sql:192` seeds exactly that.
- [x] 1.3 Create `Transfer` and `TransferItem` (§3.2) — no setters, every mutator returns a new
      instance after consulting `TransferStateMachine`, `items` copied defensively — plus the thirteen
      read records §3.2 lists and the eleven `domain/exception/` types of §3.4.
- [x] 1.4 Create `transfers/domain/service/` (§3.3): `TransferStateMachine` (R-01 as a `Map` constant,
      not a chain of `if`s), `TransferApprovalPolicy` (R-07), `TransferDispatchPolicy` (R-13, lines
      returned **already in the §7.1 lock order**), `TransferReceiptPolicy` (R-16…R-19, `discrepancy =
      dispatched − received` by construction), `TransferAccessPolicy` (visibility before side).
- [x] 1.5 Create the three `transfers/application/port/out/` interfaces and six `port/in/` use cases
      (§5). **Every method takes `AuthenticatedPrincipal`, reads included** — the branch is
      session-derived (RN-14), never a parameter.
- [x] 1.6 Create the five `transfers/application/service/` classes. Dispatch and receipt run lock →
      access → state machine → policy → port calls in lock order → save → audit → publish, publish
      **last and inside** the transaction. Audit `branchId` per T-03 (§7).
- [x] 1.7 Create `logistics/domain/` — `LogisticsRoute` with its three value objects, the §4 read
      records and four exceptions, `DelayDetectionPolicy` and `DeliveryComplianceCalculator` (§4:
      unmeasured excluded, `onTimePercentage` **`null`** when `measured == 0`, D-6) — then
      `logistics/application/`: three out-ports, four use cases, four services (§5).
- [x] 1.8 Verify no `domain/` imports a framework: `rg "org\.springframework|jakarta\.persistence"
      transfers/domain logistics/domain` returns nothing.
- [x] 1.9 Unit `*Test` (no Docker): `TransferStateMachineTest` enumerating **every** state × transition
      pair, legal and illegal (R-01, R-14, R-22, RNF-MAN-01); `TransferNotesTest` (F-1 round trip,
      missing-token default, token absent from `observations()`); value-object boundaries (§3.1).
- [x] 1.10 Unit `TransferApprovalPolicyTest` (R-07: 100→60 allowed, 120 and 0 refused),
      `TransferDispatchPolicyTest` (R-13 plus the §7.1 order), `TransferReceiptPolicyTest` (R-17, R-18,
      R-19 incl. all-zero received being valid), `TransferAccessPolicyTest` (R-03/R-05/R-06/R-21:
      third branch ⇒ *not found*, wrong side ⇒ *cross branch*), `DeliveryComplianceCalculatorTest`.
- [x] 1.11 Unit service tests with stubbed ports: audit on every mutation; the discrepancy event
      published **twice**, once per branch, and only on a shortfall (R-18).
- [x] 1.12 Run `cd backend && ./mvnw test`, then `./mvnw verify` for `ModuleBoundariesTest` and
      `SharedIsFrameworkFreeTest`. **Ship the application services unannotated** while their out-ports
      have no adapter — 2.7 restores `@Service`; registering them now breaks `ApplicationContextIT`,
      exactly as in `add-inventory-module` S1.

## Phase 2 — S2: infrastructure, web and the scheduler (PR2)

- [x] 2.1 Create `TransferJpaEntity` + `TransferItemJpaEntity` (`@OneToMany(cascade = ALL,
      orphanRemoval = true)`), FKs as plain `Long` columns, **no `@ManyToOne`**, **no `@Entity` over
      `products`/`branches`/`users`** (§6.1). `TransferMapper` is the only place the F-1 token is
      written or read, and it sets `updated_at` explicitly (no trigger exists).
- [x] 2.2 Create `TransferSpringDataRepository` with `findByExternalId` annotated
      `@Lock(LockModeType.PESSIMISTIC_WRITE)` — **no `@QueryHints` lock timeout**, PostgreSQL renders
      none (§6.1) — plus `TransferReferenceSpringDataRepository`, `TransferReferenceAdapter` and
      `TransferPersistenceAdapter`, whose `create` takes the advisory lock, derives the next
      `TRF-<yyyy>-<nnnn>` and inserts, in that order (§6.2, D-3).
- [x] 2.3 Edit `inventory`'s `StockMutationAdapter` to translate its domain exceptions into
      `StockMutationRejectedException` (D-4) and add `InventoryOutboundValuationAdapter` implementing
      `OutboundValuationPort` over `idx_kardex_reference` (D-2). `inventory`'s own use cases and
      exception handler stay untouched.
- [x] 2.4 Create `SpringTransferAlertPublisher`, `TransferController` (contract §6's seven endpoints —
      no `branchId` anywhere (RN-14), oversized page **rejected** not clamped (R-00), no numeric id and
      no raw F-1 token) and `TransfersExceptionHandler` (scoped by `basePackages`, every §7 code it
      owns, incl. `insufficient_stock` and `concurrent_transfer_update`, both 409).
- [x] 2.5 Create `logistics/…/out/persistence/`: `LogisticsRouteJpaEntity`, `LogisticsRouteMapper`,
      `LogisticsRouteSpringDataRepository`, `LogisticsReferenceSpringDataRepository`,
      `LogisticsRoutePersistenceAdapter`, and `TransferProjectionSpringDataRepository extends
      Repository<…>` with §6.3's three native queries plus `TransferMonitorReadAdapter` — **no
      `@Modifying`, no `save`/`delete`, no `@Entity` over `transfers`** (P-12 must be structural).
- [x] 2.6 Create `RouteLeadTimeAdapter` (empty for a missing or inactive route, R-24),
      `SpringLogisticsAlertPublisher`, `LogisticsController`, `LogisticsExceptionHandler`,
      `TransferDelayScheduler` and `LogisticsSchedulingConfig` carrying `@EnableScheduling` (**never**
      on `InventoryApplication`); `detect()` is `readOnly` and publishes last, or `AFTER_COMMIT` never
      fires (§6.5, D-5).
- [x] 2.7 Restore `@Service` on the nine application services from S1, or their `@Transactional`
      boundaries stay inert. Then edit `iam/…/config/SecurityConfig` with §6.4's four matcher groups,
      approval/rejection/cancellation **before** the general `/api/transfers/**` — **string literals
      only**, since importing a `transfers` type creates an `iam → transfers` edge.
- [x] 2.8 Verify every §7 code is reachable from a controller path (no dead code; list the path per
      code in the PR description), then `./scripts/validar_esquema.sh` (green **and unaffected**) and
      `./mvnw verify`.

## Phase 3 — S3: cross-cutting verification and documentation (PR3)

Testcontainers `*IT` only for invariants that can break the system; the rest is S1 units plus one
smoke assertion per controller group. **Docker-needing classes end in `IT`, never `Test`.**

- [x] 3.1 `TransferDispatchAtomicityIT` — R-11/R-12/T-01: `TRANSFER_OUT` on origin plus destination
      in-transit increment with **no** Kardex row for the shift; a forced mid-dispatch failure leaves
      state, balances and Kardex unchanged. Copy `inventory`'s atomicity fixture pattern.
- [x] 3.2 `TransferReceiptDiscrepancyIT` — R-16/R-17/R-18/R-20: 100 dispatched, 90 received ⇒ +90
      stock, discrepancy 10, in-transit **0**, `RECEIVED_WITH_DISCREPANCY`, one alert per branch; a
      full receipt gives `RECEIVED` and no alert; `TRANSFER_IN` unit cost equals `TRANSFER_OUT`'s (D-2).
- [x] 3.3 `TransferStateMachineIT` — R-01/R-22: dispatch from `REQUESTED` and cancellation from
      `IN_TRANSIT` both `409`, no balance touched.
- [x] 3.4 `TransferBranchIsolationIT` (**name it exactly this**; `BranchIsolationIT` and
      `InventoryBranchIsolationIT` already exist) — §5: a third branch gets `transfer_not_found`,
      destination cannot dispatch, origin cannot receive, `OPERATOR` denied approval, cancellation,
      monitor and report.
- [x] 3.5 `TransferConcurrencyIT` — R-12/T-02: two concurrent dispatches over the same stock, exactly
      one succeeds, stock never negative, no `500`; two simultaneous creations yield two distinct
      `transfer_number` values (D-3).
- [x] 3.6 `TransferApiSmokeIT` — one assertion per read endpoint (`GET /api/transfers`, detail,
      `/api/logistics/transfers/active`, `/api/logistics/compliance`) and route CRUD: status,
      page-envelope shape, no numeric id, no raw `PRIORITY:` token.
- [x] 3.7 Register **DT-11** in `docs/deuda_tecnica.md` (Spanish; DT-01…DT-10 are taken) — no sequence
      behind `transfer_number`, the advisory lock guards it, repayment is `CREATE SEQUENCE
      transfer_number_seq` (§9); add the summary-table row **and** the detail section. Confirm
      `/v3/api-docs` documents all fourteen operations, their statuses and the `{ code, message }`
      envelope (RNF-API-01).
- [x] 3.8 Run `python3 scripts/validar_trazabilidad.py` (green; §3 expects no `docs/` edit),
      `./scripts/validar_esquema.sh` (green, unchanged) and `cd backend && ./mvnw verify` with
      `ModuleBoundariesTest` included.
