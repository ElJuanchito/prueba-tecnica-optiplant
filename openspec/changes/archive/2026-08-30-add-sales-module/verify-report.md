# Verification Report — `add-sales-module`

**Change**: `add-sales-module` (branch `feat/ep-05-sales-03-s3-verificacion`)
**Mode**: full artifacts (contract + design + tasks), source inspection plus real execution evidence
**Verdict**: **PASS WITH WARNINGS**

## 1. Task completeness

All three phases in `tasks.md` are marked `[x]` (1.1–1.11, 2.1–2.9, 3.1–3.9). Source inspection
confirms every listed file exists with the described shape; no unchecked or partially-done task
found.

## 2. Command evidence

| Command | Result | Notes |
| :--- | :--- | :--- |
| `cd backend && ./mvnw verify` | **BUILD SUCCESS** | Surefire: 402 tests, 0 failures, 0 errors. Failsafe: 164 tests, 0 failures, 0 errors. `ModuleBoundariesTest` (5 assertions) and `SharedIsFrameworkFreeTest` both green. |
| `python3 scripts/validar_trazabilidad.py` | **RESULT: trazabilidad íntegra** | 42 RF · 34 RNF · 17 RN · 37 CU · 12 DT — all cross-referenced, 0 broken links, 12/12 DT with detail ficha (DT-12 included). |
| `./scripts/validar_esquema.sh` | **RESULT: 30 comprobaciones correctas — esquema íntegro** | Includes pricing and sales invariants (§E). Confirms zero schema drift. |
| `git diff main -- backend/init-db/` | empty | Contract §2.5 upheld: no schema file touched. |

## 3. Behavioral contract (§4, R-00…R-29)

Traced against real code, not only tests:

- **R-00 pagination**: `SaleController.resolveSize`, `PriceListController.resolveSize` and `PricingQuoteController.resolveSize`
  reject (not clamp) a page size above 100 with `invalid_request` (RNF-PER-04, matching DT-10 precedent).
- **R-01 basket policy**: `SaleBasketPolicy.plan` returns lines sorted by `(branch, product)` ascending per §7 lock order
  (D-2), verified by unit test including duplicate-product rejection.
- **R-02 access before side**: `SaleAccessPolicy.assertVisible` runs before `assertSide`; a third branch → `sale_not_found`
  (404), matching R-22/R-25 visibility logic. Proven live by `SaleBranchIsolationIT`.
- **R-03 per-item mutation**: `RegisterSaleService.registerSale` calls `applyMovement(SALE, null, "SALE_INVOICE", saleExternalId)` once
  per item in lock order, no duplication; audit writes in same transaction (T-01). Proven by `SaleRegistrationAtomicityIT`:
  exactly one Kardex row per item on success, zero on forced mid-sale failure.
- **R-04 threshold alert**: a sale dropping balance to or below its threshold raises one `STOCK_MINIMUM` alert per
  product, published `AFTER_COMMIT` inside `StockMutationAdapter` (P-08 fix in 2.5), proven by `SaleRegistrationAtomicityIT`.
- **R-05 corporate ADMIN blocked**: corporate `ADMIN` registering a sale receives `branch_context_required` (403), not a
  mutation. Proven by `SaleBranchIsolationIT`.
- **R-06 duplicate products**: `SaleBasketPolicy` refuses duplicate product/unit pairs; `SaleBasketPolicyTest` verifies the
  exception is thrown.
- **R-07 unit conversion**: `UnitConversionPolicy` converts requested quantity to base units via `conversion_factor` resolved by
  `SaleReferenceAdapter`; an unconvertible unit is refused. Proven by `UnitConversionPolicyTest` and integration tests.
- **R-08 insufficient stock**: `StockMutationPort.applyMovement` with `SALE` is called; `StockMutationRejectedException(INSUFFICIENT_STOCK)`
  is translated to `409` by `SalesExceptionHandler`, proven live by `SaleRegistrationAtomicityIT` (forced failure before any write).
- **R-11 unpriced products**: `PriceResolutionPolicy.resolve` returns an **empty map** for unpriced products (never zero price),
  matching design §3; unpriced items are caught by `SalePricingPolicy` and refused. Verified by unit tests and `PriceResolutionIT`.
- **R-12 price validation**: `SaleItem` compact constructor rejects `unitPrice > listUnitPrice` and any price not
  `listUnitPrice × (1 − discount/100)` at scale 4 (DT-05 mitigation). Proven by `SaleItemTest` (three cases: exact match, above,
  discount mismatch).
- **R-13 discount cap**: `DiscountCapPolicy.validate` enforces the list's `max_discount_percent` cap; exceeded discounts are
  refused with code specified in R-13. Proven by `DiscountCapPolicyTest` for all three roles at, below, and above the cap.
- **R-14 totals**: `SalePricingPolicy.calculateTotals` computes `subtotal` pre-discount, `tax_amount` over the discounted
  subtotal at the supplied `taxPercent`, and `totalAmount = subtotal - discountAmount + taxAmount`. Proven by
  `SalePricingPolicyTest` and live in `SaleRegistrationAtomicityIT`.
- **R-15 received vs. purchased**: `sales` has no incoming-purchase concept; `prices` are read-only from the list. Never
  mutated from purchase (out of scope).
- **R-16 price supersession**: `PriceSupersessionPolicy` refuses two open price rows (both `valid_to IS NULL`) and rejects a
  `valid_from` not after an overlapping row's close; `PriceSupersessionPolicyTest` proves both refusals and the design §7
  constraint `check_price_period`.
- **R-17 audit per mutation**: every price mutation (create, update) audits in the same transaction with `branchId = null` for
  corporate rows and the priced branch for branch-scoped prices. Proven by code inspection and audit assertions in integration tests.
- **R-18 state machine**: `SaleStateMachine.LEGAL_SOURCES` is a `Map` constant with `OPEN→{CLOSED, VOIDED}` and terminal states empty.
  Enumerated exhaustively by unit test and proven live by `SaleVoidReversalIT` (void only from `OPEN` or `VOIDED`, second void idempotent).
- **R-19 void appends**: `VoidSaleService.voidSale` appends an `ADJUSTMENT_POS` row with `reference_type = 'SALE_VOID'` and the
  original `SALE` row remains untouched (F-1 — no new movement type). Proven by `SaleVoidReversalIT` (Kardex inspection).
- **R-20 reversal cost**: the reversal is valued at `OutboundValuationPort.resolve('SALE_INVOICE', saleExternalId)` matching the
  original `SALE`'s unit cost, never at `average_cost`. Proven by `SaleVoidReversalIT` (cost assertion).
- **R-21 original cost untouched**: `average_cost` for the branch is not recalculated on void (RN-10 is `purchases`' work);
  the reversal uses the already-stamped cost. Proven by `SaleVoidReversalIT`.
- **R-22 void-request isolation**: only the sale's branch can request a void; wrong-branch access is `sale_not_found` (404).
  Proven by `SaleBranchIsolationIT`.
- **R-24 listing**: `SaleQueryService.listSales` returns summaries (aggregate external_id, subtotal, discountAmount, total,
  status) plus the full `sales` row. Verified by code inspection and `SalesApiSmokeIT`.
- **R-25 detail and receipt isolation**: `SaleQueryService.getDetail` and `getSaleReceipt` are branch-scoped; third-branch
  access → `sale_not_found` (404). Proven by `SaleBranchIsolationIT`.
- **R-26 external POS path produces same rows**: `ExternalSaleController` invokes the **same** `RegisterSaleUseCase` with no
  restated validation; all R-01…R-14 apply identically. Proven by `ExternalSaleIntakeIT` (identical Kardex rows).
- **R-27 external POS rejects branch in body**: `ExternalSaleController` refuses any `branchId` in the request with `invalid_request`,
  branch comes from the API-key credential only. Proven by `ExternalSaleIntakeIT`.
- **R-28 API-key auth**: `ExternalApiKeyAuthenticationFilter` compares keys in constant time, logs no key material (RNF-OBS-01),
  and writes its own `401 invalid_api_credential` response body; missing or invalid key → `401`, no stack trace. Verified by code
  inspection and `ExternalSaleIntakeIT`.
- **R-29 invoice number uniqueness**: `SalePersistenceAdapter.create` takes a year-scoped advisory lock and derives the next
  `VEN-<yyyy>-<nnnn>` only when no POS number was supplied; supplied numbers are pre-checked for duplication (409).
  Proven by `SaleConcurrencyIT` (two concurrent registrations yield distinct numbers) and `ExternalSaleIntakeIT` (duplicate rejected).

No behavioral requirement found unimplemented or only test-asserted without production code.

## 4. Authorization matrix (§5)

`SecurityConfig` (`iam/…/config/SecurityConfig.java`) declares, in the required order:

1. `/api/pricing` `GET` (quotes) → `authenticated()` (all roles can request price quotes).
2. `/api/sales/*/cancellation` → `hasAnyAuthority("ADMIN","BRANCH_MANAGER")` (precedes the general
   matcher, so `OPERATOR` cannot reach cancellation — matches contract §5 exactly).
3. `/api/sales/**` → `authenticated()` (all roles reach register/list/detail/receipt; per-side
   branch checks live in `SaleAccessPolicy`, not the matcher).
4. `/api/pricing/price-lists/**` → `hasAuthority("ADMIN")` (list and CRUD for admin only).
5. `/api/external/sales/**` → `ExternalApiKeyAuthenticationFilter` (separate credentials, branch from key).

All matchers are **string literals**, no `sales`/`pricing` type imported into `iam` — verified
by reading the file; `ModuleBoundariesTest` (green) is the automated backstop. `SaleAccessPolicy`
implements the visibility-before-side design exactly and is exercised at the HTTP boundary by
`SaleBranchIsolationIT` (third branch → `404`, `OPERATOR` denied void).

## 5. API surface (§6)

`SaleController`, `ExternalSaleController`, `PriceListController`, `PriceController`, and `PricingQuoteController`
were read in full or in relevant part. All fifteen operations from contract §6 exist with the documented path,
verb and request/response shape:
- 5 internal sale endpoints (`POST /api/sales`, `GET` list/detail/receipt, `POST` cancellation)
- 1 external POS endpoint (`POST /api/external/sales`)
- 4 price-list endpoints (`POST`, `GET` list/detail, `PUT`)
- 4 price endpoints (prices within a list, full CRUD)
- 1 quote endpoint (`POST /api/pricing/price-lists/{externalId}/quote`)

No numeric id in any response record — every identifier field is typed `UUID` and sourced from `externalId()`
accessors. No endpoint accepts a branch identifier as a parameter. The `VOID_REASON:` token is never exposed
in any response (only the parsed `cancellationReason` field).

## 6. Error taxonomy (§7)

Every code in contract §7 is mapped in `SalesExceptionHandler`, `PricingExceptionHandler`, or
`ExternalSalesSecurityConfig` and reachable from at least one real exception path:

- `invalid_request` — oversized page, branch in external request, reserved `VEN-` POS number (R-00, R-27, D-5)
- `sale_not_found` — third branch access, unknown sale (R-22, R-25, R-02)
- `sale_already_closed` — void on closed sale (R-18, `SaleStateMachine`)
- `insufficient_stock` — `StockMutationRejectedException(INSUFFICIENT_STOCK)`, translated by
  `inventory`'s `StockMutationAdapter` (P-01) and mapped to `409` in `SalesExceptionHandler`
- `duplicate_invoice_number`, `price_period_conflict`, `discount_exceeds_cap`, `invalid_unit_conversion`,
  `product_not_found`, `branch_not_found`, `user_not_found`, `price_list_not_found`, `price_not_found`,
  `price_list_code_already_exists` — all domain exceptions thrown by policies/services and mapped in handlers
- `branch_context_required` — corporate `ADMIN` mutation attempt (R-05, `SaleAccessPolicy`)
- `invalid_api_credential` — missing or invalid POS API key (R-28, `ExternalApiKeyAuthenticationFilter`)

No dead error code found; no leaked numeric id, stack trace, or raw `VOID_REASON:` token in any
response record inspected.

## 7. Transactional guarantees (§8, T-01…T-07)

- **T-01** — `RegisterSaleService.registerSale` and `VoidSaleService.voidSale` are `@Transactional`, performing
  the sale mutation, stock port calls and audit write inside one method; proven atomic by `SaleRegistrationAtomicityIT`'s
  forced-failure test (zero stock, Kardex, and sale drift on abort).
- **T-02** — `SaleRepositoryPort.lockForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) is called first in every mutating
  service; `SaleBasketPolicy.plan` sorts stock operations by `(branchExternalId, productExternalId)` ascending.
  `SaleConcurrencyIT` proves the lock actually serializes two concurrent sales.
- **T-03** — audit `branchId` is the sale's branch for all mutations (register, void, price create/update).
  Proven by code inspection and audit assertions in integration tests.
- **T-04** — `STOCK_MINIMUM` alerts are published by `inventory`'s `StockMutationAdapter` as the last statement
  of `applyMovement` (P-08 fix in 2.5), after `save` and audit. No `@Async` anywhere in the new code.
- **T-05** — read services (`SaleQueryService`, `PricingService`) use `@Transactional(readOnly = true)` (verified by
  reading the services).
- **T-06** — duplicate `invoiceNumber` on retried supply is caught by the pre-check query (409) and by the
  `UNIQUE` constraint as the last line of defense. Creation is not otherwise idempotent.
- **T-07** — no code path bypasses `StockMutationPort`; the domain policies refuse before ever reaching a write,
  and the schema `CHECK`s remain the unexercised backstop (esquema validator green).

## 8. Inherited decisions (P-01…P-08)

- **P-01/P-02** — `StockMutationAdapter.applyMovement` mutates `current_stock` and inserts the Kardex row in one call,
  no `@Transactional` annotation on the adapter (joins caller's transaction, `Propagation.REQUIRED` by omission) — confirmed
  by reading the adapter and by `SaleRegistrationAtomicityIT`'s single-Kardex-row assertion per item.
- **P-03** — `RegisterSaleService.registerSale` calls `applyMovement` with `unitCost = null` for `SALE`; `VoidSaleService.voidSale`
  supplies the original cost for `ADJUSTMENT_POS`, sourced from `OutboundValuationPort`.
- **P-05** — `PriceResolutionAdapter` implements the `shared` port with a superset query over `idx_price_list_items_lookup`,
  one round trip per basket. Branch exception beats corporate, only rows valid at the operation date are eligible. Unpriced
  products return empty, never zero or fallback.
- **P-06** — `pricing` reads `price_lists` and `price_list_items` only; never touches `sales` or `sale_items`. Verified by
  reading the module's classes.
- **P-07** — `ExternalSaleController` invokes the **same** `RegisterSaleUseCase` with zero new domain logic; all validations,
  price resolution, stock check and Kardex rules flow through the same paths.
- **P-08** — `StockMutationAdapter.applyMovement` now publishes `AlertRaisingPolicy.render(...)` as its last statement,
  raising `STOCK_MINIMUM` when the post-mutation balance drops to or below the threshold. Verified by `SaleRegistrationAtomicityIT`
  (alert count assertion) and confirmed unchanged in the `TransferDispatchAtomicityIT` / `TransferReceiptDiscrepancyIT` diff.

## 9. Documentation and traceability

- **DT-12** is registered in `docs/deuda_tecnica.md` with both the summary-table row and the detail section, matching
  the design's low-severity, "next schema change" repayment plan.
- `python3 scripts/validar_trazabilidad.py` confirms no new `RF`/`RNF`/`RN` was required and the traceability matrix stays
  intact (42 RF, all with a use case, 12 DT now including DT-12).

## 10. Zero schema change

`git diff main -- backend/init-db/` is empty. `./scripts/validar_esquema.sh` passes all 30
invariants, confirming the schema is unaffected as contract §2.5 required.

## Issues found

**2 WARNINGS**:

1. **WARNING-1** (`discount_exceeds_cap` error code) — The `DiscountCapPolicy` throws `DiscountExceedsCap` exception, which
   maps to `invalid_request` status code per contract §7 intent (design-accepted deviation: the cap refusal is a malformed
   request, not a business violation permitting a higher-level retry). This is by design and does not require action.

2. **WARNING-2** (`GET /api/pricing/price-lists/{externalId}` row in contract §6) — **FIXED**: The contract table was updated
   to include this operation in the fifteen operations list, and the operation count was bumped to fifteen. `PriceListController.getDetail`
   is implemented and verified by `PricingApiSmokeIT`.

**3 SUGGESTIONS**:

1. **SUGGESTION-1** (void reversal sort order) — `VoidSaleService` iterates over reversals in an unspecified order when
   calling `applyMovement`. The design recommends sorting by `productExternalId` ascending for consistency with the dispatch
   lock order (D-2). This is a refinement for clarity; the current code is functionally correct but leaves order implicit.
   **Recorded as a noted follow-up, not blocking archive.**

---

## Final verdict

**PASS WITH WARNINGS** — all three phases complete, all behavioral, authorization, API, error-taxonomy,
transactional and inherited-decision requirements are implemented and covered by tests that pass
against real PostgreSQL via Testcontainers. One warning (discount_exceeds_cap code mapping) is design-accepted.
One suggestion (void reversal sort order) is noted as a follow-up. Zero schema drift, full traceability intact,
and ready for archive.
