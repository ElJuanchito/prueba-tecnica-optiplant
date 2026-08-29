# Tasks: `add-inventory-module`

Three phases, one PR each: domain+application → infrastructure+web → verification. `contract.md`
§10 is the definition of done; `design.md` is the shape. **Zero schema change:** no task edits
`backend/init-db/` — if one seems to need it, stop and report. **The layer pattern is replicated,
not invented:** `JpaEntity → Mapper → SpringDataRepository → PersistenceAdapter → Controller →
ExceptionHandler` already exists in `catalog` — read that counterpart before writing each new file.

## Phase 1 — S1: `inventory` and `notifications` domain and application (PR1)

- [ ] 1.1 Create `shared/stock/`: `StockMovementType` (the eight `01-init-schema.sql:215-226`
      literals + `isInbound()` / `requiresSuppliedCost()`), `StockMutationPort`,
      `StockMutationCommand`, `InTransitShiftCommand`, `InTransitDirection` — design §2.1 verbatim.
      Javadoc must state no implementation may be `@Async` or `AFTER_COMMIT` (P-01).
- [ ] 1.2 Create `shared/alert/`: `OperationalAlertRaised`, `AlertType`, `AlertSeverity`
      (design §2.2, D-1). No `title` field — `notifications` derives it.
- [ ] 1.3 Create `inventory/domain/model/` value objects `Quantity`, `StockLevel`, `UnitCost`,
      `MovementReason`, `DateRange` with the invariants of design §3.1.
- [ ] 1.4 Create `inventory/domain/model/BranchInventory` and `KardexMovement` (design §3.2).
      `KardexMovement` gets **no** `with*` method and no setter (R-17).
- [ ] 1.5 Create the read records: `StockLine`, `StockPage`, `BranchAvailability`, `KardexLine`,
      `NetworkAvailability`, `KardexPage`, `ThresholdView`, `MovementReceipt`, `ProductDescriptor`,
      `StockThresholdBreach`.
- [ ] 1.6 Create `inventory/domain/exception/` — the eight of design §3.4. Then
      `inventory/domain/service/StockMutationPolicy` (design §3.3): it returns a
      `MovementDraft` carrying both the updated balance and the movement, so neither is obtainable
      without the other (RN-02), plus `AdjustmentPolicy`, `AlertRaisingPolicy`, `BranchScopePolicy`.
- [ ] 1.7 Create `inventory/application/port/out/`: `BranchInventoryRepositoryPort`,
      `KardexRepositoryPort`, `ProductLookupPort`, `AlertEventPublisherPort` (design §5.2).
- [ ] 1.8 Create `inventory/application/port/in/`: `QueryStockUseCase`, `QueryKardexUseCase`,
      `RegisterStockMovementUseCase`, `ManageStockThresholdUseCase` (design §5.1). **Every method
      takes `AuthenticatedPrincipal`, reads included** — the branch is session-derived (RN-14).
- [ ] 1.9 Create `inventory/application/service/StockMovementService` — `@Transactional`;
      lock → policy → save → append → audit → publish, with publish **last and inside** the
      transaction (design §11 trap 4). Audit `branchId` = branch of the mutated resource (T-03).
- [ ] 1.10 Create `StockQueryService` (`readOnly`, batch product lookup, no N+1; R-01…R-05),
      `StockThresholdService` (R-14, R-15) and `KardexQueryService` (R-16, R-19).
- [ ] 1.11 Create `notifications/domain/`: `Alert`, `AlertDedupKey` (its `title()` is the only writer
      of the F-1 token) and the two exceptions (design §4).
- [ ] 1.12 Create `notifications/application/`: `AlertRepositoryPort`, `ManageAlertsUseCase`,
      `AlertService` (design §5.2, R-21…R-24).
- [ ] 1.13 Verify neither `domain/` imports Spring or Jakarta:
      `rg "org\.springframework|jakarta\.persistence" inventory/domain notifications/domain`.
- [ ] 1.14 Unit `*Test` (no Docker) for the value objects — boundaries and rejections (§3.1) — and
      `StockMutationPolicyTest`: P-03 cost present-when-forbidden and absent-when-required both
      rejected, R-11 insufficient stock, R-12 outbound valued at `averageCost`, scale 4.
- [ ] 1.15 Unit `AdjustmentPolicyTest` (R-06: 100 counted 92 → `ADJUSTMENT_NEG` of 8; R-08 equal and
      negative counts), `AlertRaisingPolicyTest` (R-20 severity), `BranchScopePolicyTest` (PA-02).
- [ ] 1.16 Unit `AlertDedupKeyTest` (R-21 token shape, ≤150 chars) and `AlertServiceTest`
      (dedup hit writes nothing; R-23 double resolve refused).
- [ ] 1.17 Unit `StockMovementServiceTest` with stubbed ports — audit written on every mutation,
      event published exactly when `breachesThreshold()` and not otherwise.
- [ ] 1.18 Run `cd backend && ./mvnw test` (surefire only, no Docker), then `./mvnw verify` for
      `ModuleBoundariesTest` and `SharedIsFrameworkFreeTest`.

## Phase 2 — S2: infrastructure, web and the alert listener (PR2)

- [ ] 2.1 Create `inventory/…/out/persistence/BranchInventoryJpaEntity` and `KardexMovementJpaEntity`
      — FKs mapped as plain `Long` columns, **no `@ManyToOne`**, and **no `@Entity` for
      `products`/`branches`/`users`** (design §6.1). Add `BranchInventoryMapper`, `KardexMovementMapper`.
- [ ] 2.2 Create `BranchInventorySpringDataRepository` with the lock query annotated
      `@Lock(LockModeType.PESSIMISTIC_WRITE)`. **No `@QueryHints` lock timeout** — PostgreSQL renders
      no numeric timeout (design §7, verified against `hibernate-core-7.4.5.Final`).
- [ ] 2.3 Create `KardexMovementSpringDataRepository` — query methods only, no `delete*`, no
      `@Modifying` update (R-17).
- [ ] 2.4 Create `ForeignKeyResolverSpringDataRepository` — native `@Query` resolving `external_id
      → id` for `products`/`branches`/`users`, the `ProductDescriptor` projection, and the
      active-branch list (design §6.1).
- [ ] 2.5 Create `BranchInventoryPersistenceAdapter` — `createZeroed` sets `min_stock_threshold`
      **explicitly to 0**, never inheriting the default `10.0000` (design §8, F-3). Then
      `KardexPersistenceAdapter` and `ProductLookupAdapter`.
- [ ] 2.6 Create `…/out/stock/InventoryStockPresenceAdapter` implementing
      `shared/stock/ProductStockPresencePort` — `catalog`'s base-unit rule stops answering `UNKNOWN`
      — and `…/out/event/SpringAlertEventPublisher` wrapping `ApplicationEventPublisher`.
- [ ] 2.7 Create `…/in/web/InventoryController` — the six endpoints of contract §6. No `branchId` in
      path, query or body (RN-14); page size clamped server-side; no numeric id in any payload.
- [ ] 2.8 Create `InventoryExceptionHandler` mirroring `CatalogExceptionHandler`, mapping every
      contract §7 code — incl. `PessimisticLockingFailureException` → `concurrent_stock_update` 409.
- [ ] 2.9 Create `notifications/…/out/persistence/`: `SystemAlertJpaEntity`, `AlertMapper`,
      `AlertSpringDataRepository` (advisory-lock native query + dedup query on
      `branch_id, alert_type, title, is_resolved = false`), `AlertPersistenceAdapter`.
- [ ] 2.10 Create `notifications/…/in/event/OperationalAlertListener` —
      `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)`,
      whole body in a `try/catch (RuntimeException)` that logs and returns (P-10, design §6.3).
- [ ] 2.11 Create `notifications/…/in/web/AlertController` and `NotificationsExceptionHandler`.
- [ ] 2.12 Edit `iam/infrastructure/config/SecurityConfig` — add the five matchers of design §6.2
      before `anyRequest()`. **String literals only**: importing an `inventory` type there creates an
      `iam → inventory` edge and fails `ModuleBoundariesTest`.
- [ ] 2.13 Verify every contract §7 code is reachable from a controller path — no dead error code;
      list the path per code in the PR description.
- [ ] 2.14 Run `./scripts/validar_esquema.sh` (green **and unaffected**), `./mvnw verify`.

## Phase 3 — S3: cross-cutting verification and documentation (PR3)

Testcontainers `*IT` only for invariants that can break the system; the rest is S1 units plus one
smoke assertion per controller group.

- [ ] 3.1 `KardexAtomicityIT` — R-18: the Kardex replayed from `INITIAL_LOAD` equals `current_stock`;
      a forced failure after the balance update leaves neither the balance change nor the Kardex row
      (T-01). Copy the `AuditAtomicityFixtureService` fixture pattern.
- [ ] 3.2 `InventoryBranchIsolationIT` — R-01, R-19, R-24. **Name it exactly this**:
      `BranchIsolationIT` already exists from `iam` and would collide (design §11 trap 1).
- [ ] 3.3 `StockValidationIT` — R-11 / RN-01 / T-02: two concurrent write-offs serialize on
      `FOR UPDATE`; one succeeds, stock never negative, loser gets `insufficient_stock` 409, no 500.
- [ ] 3.4 `StockAlertIT` — R-20, R-21, R-24: the alert exists after commit, is not duplicated across
      repeated breaching movements, is not auto-resolved when stock recovers (R-22), and a forced
      listener failure does not roll back its movement.
- [ ] 3.5 `InventoryApiSmokeIT` — one assertion per read endpoint (`/stock`, `/stock/{id}/network`,
      `/kardex`, `/alerts`) plus the threshold `PUT`: status, page-envelope shape, no numeric id.
- [ ] 3.6 `InventoryRbacIT` — the §5 matrix: `OPERATOR` denied adjustments/Kardex/alerts and allowed
      write-offs (R-13); corporate `ADMIN` gets `branch_context_required` on a mutation (PA-02).
- [ ] 3.7 Confirm `ProductCatalogIT`'s base-unit expectation still holds now that 2.6 turned
      `catalog`'s fail-closed `UNKNOWN` into a real `ProductStockPresencePort` answer.
- [ ] 3.8 Register **DT-09** in `docs/deuda_tecnica.md` (Spanish; DT-01…DT-08 are taken) — alert
      dedup has no schema uniqueness, the advisory lock guards it, repayment is the partial unique
      index (design §9). Add both the summary-table row and the detail section.
- [ ] 3.9 Confirm `/v3/api-docs` documents all eight operations with statuses and the
      `{ code, message }` envelope (RNF-API-01).
- [ ] 3.10 Run `python3 scripts/validar_trazabilidad.py` (green; no identifier added),
      `./scripts/validar_esquema.sh` (green, unchanged) and `cd backend && ./mvnw verify`.
