# Tasks: `add-sales-module`

Three phases, one PR each, matching `contract.md` §10 and `openspec/PLAN.md` §3; `design.md` is cited by section. **Zero
schema change**: if a task seems to need one, §2.5 was wrong — stop and report. The `JpaEntity → Mapper → Repository →
PersistenceAdapter → Controller → ExceptionHandler` chain already exists in `inventory` and `transfers`: replicate it,
never invent it.

## Phase 1 — S1: `sales` and `pricing` domain and application (PR1)

- [x] 1.1 Create `shared/price/PriceResolutionPort` and `AppliedPriceList` (design §2, P-05, D-1) — four methods, with
      Javadoc naming why the two list lookups are separate (both §7 error codes stay reachable) and why an unpriced
      product is *absent from the map*, never zero (R-11).
- [x] 1.2 Create `pricing/domain/`: the five value objects of §3, `PriceList`, `Price`, `PriceScope` (`is_default` gets
      no mutator), `PriceResolutionPolicy` (RN-16/R-11 — a pure fold, branch beats corporate, only rows valid at the
      date), `PriceSupersessionPolicy` (R-16, D-7 — both refusals, including the `valid_from` guard `check_price_period`
      would trip) and `QuoteCalculator` (D-6); plus the seven exception types.
- [x] 1.3 Create `pricing/application/`: `PriceListRepositoryPort`, `PriceRepositoryPort`, `PricingReferencePort`;
      `ManagePriceListsUseCase`, `ManagePricesUseCase`, `QuotePricesUseCase` (§5) and their three services — every
      mutation audits in the same transaction (R-17, T-03: `null` branch for corporate rows, the priced branch for a
      branch-scoped price).
- [x] 1.4 Create `sales/domain/model/` value objects (§4), including `InvoiceNumber` with `isReservedInternal()`
      matching `VEN-\d{4}-\d+` (D-5) and `SaleNotes`, the **only** reader and writer of the F-3 `VOID_REASON:` token
      (its `parse` must not throw on notes carrying none); then `Sale`, `SaleItem`, `SaleTotals`, `SaleStatus` — no
      setters. `SaleItem`'s compact constructor is `DT-05`'s mitigation (R-12): it rejects `unitPrice > listUnitPrice`
      **and** any `unitPrice` that is not `listUnitPrice × (1 − discount/100)` at scale 4.
- [x] 1.5 Create `sales/domain/service/` (§4.1): `SaleBasketPolicy` (R-01/R-06, lines returned **already in the §7 lock
      order**), `UnitConversionPolicy` (R-07/RN-13), `DiscountCapPolicy` (R-13/RN-17), `SalePricingPolicy` (R-14,
      `subtotal` pre-discount), `SaleStateMachine` (R-18 as a `Map` constant), `SaleAccessPolicy` (R-02/R-22/R-25 —
      visibility *before* side, 404 never 403); plus the fourteen exception types of §4.1.
- [x] 1.6 Create `sales/application/`: `SaleRepositoryPort`, `SaleReferencePort` (§5); `RegisterSaleUseCase` (its
      command carries the **optional** `invoiceNumber`, P-07/R-29), `VoidSaleUseCase`, `QuerySalesUseCase`; and their
      three services. Register: access → basket → list → prices → cap → totals → create → per line in lock order
      `applyMovement(SALE, null, "SALE_INVOICE", saleExternalId)` → audit (R-03, T-01). Void: `lockForUpdate` → access →
      state machine → `outboundUnitCosts` → `applyMovement(ADJUSTMENT_POS, "SALE_VOID", original cost)` → save → audit
      (R-18…R-21, F-1/F-2). **No alert publisher port** (P-08).
- [x] 1.7 Unit `PriceResolutionPolicyTest` (RN-16: branch beats corporate, an expired row is ineligible, no eligible row
      ⇒ empty) and `PriceSupersessionPolicyTest` (R-16: normal close, two open rows refused, a `validFrom` not after the
      open row's refused — D-7).
- [x] 1.8 Unit `DiscountCapPolicyTest` (R-13/RN-17 at, below and above the cap, for all three roles),
      `SalePricingPolicyTest` (R-14 totals including tax over the discounted subtotal) and `SaleItemTest` (R-12/`DT-05`:
      a `list_unit_price` inconsistent with the resolved price is rejected).
- [x] 1.9 Unit `SaleStateMachineTest` (R-18, every state × transition incl. the double void),
      `UnitConversionPolicyTest` (R-07 incl. the unconvertible unit), `SaleBasketPolicyTest` (R-01/R-06 incl. the lock
      ordering), `SaleAccessPolicyTest` (R-02/R-22/R-25: third branch ⇒ *not found*, `OPERATOR` ⇒ cross-branch) and
      `SaleNotesTest` (F-3 round trip, missing token, token absent from the exposed note).
- [x] 1.10 Unit service tests with stubbed ports: `applyMovement` called once per line **in product order** with
      `unitCost = null` (P-03); the void values each reversal at the `OutboundValuationPort` cost and never touches
      `average_cost` (R-21); an audit entry on every mutation (T-01/T-03).
- [x] 1.11 Verify no `domain/` imports a framework (`rg "org\.springframework|jakarta\.persistence" sales/domain
      pricing/domain` returns nothing) and that `shared/price` names no module type; then run `cd backend && ./mvnw
      test` and `./mvnw verify` for `ModuleBoundariesTest` and `SharedIsFrameworkFreeTest`. **Ship the six application
      services unannotated** while their out-ports have no adapter — 2.8 restores `@Service`, and registering them now
      breaks `ApplicationContextIT` exactly as in `add-inventory-module` (design §12).

## Phase 2 — S2: infrastructure, web and the P-08 fix (PR2)

- [x] 2.1 Create `pricing/…/out/persistence/`: `PriceListJpaEntity`, `PriceListItemJpaEntity`, their mappers, the three
      Spring Data repositories (`PriceList`, `PriceListItem`, `PricingReference`) and the three persistence adapters.
      FKs are **plain `Long` columns, no `@ManyToOne`, no `@Entity` over `products`/`branches`** (§6.1).
- [x] 2.2 Create `pricing/…/out/price/PriceResolutionAdapter` implementing the `shared` port with §6.2's **superset**
      query over `idx_price_list_items_lookup` — one round trip per basket (RNF-PER-02), ordering left to the domain.
- [x] 2.3 Create `SaleJpaEntity` + `SaleItemJpaEntity` (`@OneToMany(cascade = ALL, orphanRemoval = true)`), `SaleMapper`
      — the only place the F-3 token is written or read — and `SaleSpringDataRepository` with `findByExternalId`
      annotated `@Lock(PESSIMISTIC_WRITE)`, **no `@QueryHints` timeout** (§6.1, F-7). `sales` has no `updated_at`.
- [x] 2.4 Create `SalePersistenceAdapter`: `create` takes the year-scoped advisory lock, derives the next
      `VEN-<yyyy>-<nnnn>` and inserts **only when no POS number was supplied** (§6.3, D-5); a supplied number is
      pre-checked for duplication (`duplicate_invoice_number` 409, R-29/T-06); listing returns summaries plus the
      aggregate row (R-24), detail and receipt lookup branch-scoped (R-25). Then `SaleReferenceSpringDataRepository` /
      `SaleReferenceAdapter` with §6.2's native queries: branch/product/user resolution, batched product descriptors,
      `conversion_factor` (D-2) and the external-credential subject for 2.7.
- [x] 2.5 **P-08** — edit `inventory/infrastructure/adapter/out/stock/StockMutationAdapter.java` only (design §8):
      inject `AlertEventPublisherPort`, capture `save(...)`'s return, and publish `AlertRaisingPolicy.render(...)` as
      `applyMovement`'s last statement; leave `shiftInTransit` untouched. Then re-read `TransferDispatchAtomicityIT` and
      `TransferReceiptDiscrepancyIT` — a dispatch now raises `STOCK_MINIMUM` where it raised nothing, so their fixtures
      may need adjusting (R-08, T-04).
- [x] 2.6 Create `SaleController` (contract §6's five internal endpoints — no branch anywhere (RN-14), oversized page
      **rejected** not clamped (R-00), no numeric id, no raw `VOID_REASON` token) and `SalesExceptionHandler` scoped to
      `…sales.infrastructure.adapter.in` — **the whole `in` package**, or 2.7's controller has no handler (§6.4 trap).
- [x] 2.7 Create the external path (§6.5, F-6, P-07): `ExternalApiKeyProperties` and `ExternalSalesSecurityConfig` in
      `sales/infrastructure/config` (an `@Order(1)` chain with `securityMatcher("/api/external/sales/**")`),
      `ExternalApiKeyAuthenticationFilter` (constant-time compare, writes its own `401 invalid_api_credential` body,
      logs no key material — R-28, RNF-OBS-01) and `ExternalSaleController`, which invokes the **same**
      `RegisterSaleUseCase` and restates no rule; a branch field in the body, or a reserved `VEN-` number, is `400
      invalid_request` (R-27, D-5).
- [x] 2.8 Create `PriceListController`, `PriceController` and `PricingQuoteController` (contract §6's seven pricing
      endpoints) plus `PricingExceptionHandler` mapping `price_list_code_already_exists`, `price_period_conflict`,
      `price_list_not_found` and `price_not_found` (§7). Then restore `@Service` on the six S1 services.
- [x] 2.9 Edit `iam/…/config/SecurityConfig` with §6.4's five matchers **in that order** — quotes and `GET /api/pricing`
      before the `ADMIN`-only rule, `/api/sales/*/cancellation` before `/api/sales/**` — **string literals only**, since
      importing a `sales` type there creates `iam → sales`. Then verify every §7 code is reachable from a controller
      path (name the path per code in the PR description — no dead error code), run `./scripts/validar_esquema.sh`
      (green **and unaffected**, §2.5) and `cd backend && ./mvnw verify`.

## Phase 3 — S3: cross-cutting verification and documentation (PR3)

**Docker-needing classes end in `IT`, never `Test`** — `*IT` is reserved for invariants that can break the system.

- [x] 3.1 `SaleRegistrationAtomicityIT` — R-03/R-04/R-08/T-01: stock decremented and one `SALE` Kardex row per item with
      `reference_type = 'SALE_INVOICE'`; a forced mid-sale failure leaves sale, balances and Kardex untouched; a sale
      crossing the threshold raises one `STOCK_MINIMUM` alert per product (P-08); insufficient stock writes nothing.
- [x] 3.2 `SaleConcurrencyIT` — R-05/T-02: two concurrent sales over the last unit, exactly one succeeds, stock never
      negative, no `500`; two simultaneous registrations yield two distinct `invoice_number` values (D-5).
- [x] 3.3 `SaleVoidReversalIT` — R-19/R-20/R-21: the void adds an `ADJUSTMENT_POS` with `reference_type = 'SALE_VOID'`
      at the original unit cost, the original `SALE` row survives, replaying the Kardex reproduces `current_stock`, and
      `average_cost` is unmoved.
- [x] 3.4 `SaleBranchIsolationIT` (**name it exactly this**; three `*BranchIsolationIT` already exist) — R-25/§5: branch
      A gets `sale_not_found` for branch B's sale, an `OPERATOR` is refused the void, and a corporate `ADMIN`
      registering gets `branch_context_required`.
- [x] 3.5 `PriceResolutionIT` — R-11/R-16/RN-16: a branch exception beats corporate, the seeded expired row
      (`50000000-…-0010`) is ignored, and a second current price is refused with `price_period_conflict`.
- [x] 3.6 `ExternalSaleIntakeIT` — R-26/R-27/R-29: the POS path produces the same rows as the internal path, ignores any
      branch in the body, and refuses a retried receipt number, an absent key and an unknown key.
- [x] 3.7 `SalesApiSmokeIT` and `PricingApiSmokeIT` — one assertion per read endpoint and price-list CRUD: status,
      page-envelope shape, aggregates present, no numeric id, no raw `VOID_REASON` token (RNF-API-02).
- [x] 3.8 Register **DT-12** in `docs/deuda_tecnica.md` (Spanish; DT-01…DT-11 are taken) — no sequence behind
      `sales.invoice_number`, repayment `CREATE SEQUENCE sale_invoice_number_seq` — with both the summary-table row and
      the detail section. Update `openspec/PLAN.md` §1–§2 and confirm `/v3/api-docs` documents all fourteen operations
      (RNF-API-01).
- [x] 3.9 Run `python3 scripts/validar_trazabilidad.py` (green; §3 expects no `docs/` requirement edit),
      `./scripts/validar_esquema.sh` (green, unchanged) and `cd backend && ./mvnw verify`.
