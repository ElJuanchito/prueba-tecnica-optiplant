# Design — `add-sales-customers`

Step 2 of the cycle. `contract.md` is authoritative; D-1…D-6, R-C0…R-C15, T-C1…T-C7, S-1…S-3, §6, §7
and §8 are settled and are **cited, never restated**. This file decides only what the contract and the
already-shipped `sales` pattern leave open. The change adds **13 classes** and modifies **20**, all
inside `sales`: no new module (D-1), no domain service, no domain event (T-C7), no second query stack.

---

## 1. Placement — every class this change introduces

All inside `com.optiplant.inventory.sales`; nothing in a direct subpackage of the base package.

| Package | Class | Purpose |
| :--- | :--- | :--- |
| `domain/model` | `Customer` | aggregate; record; `disable()`, `enable()`, `withDetails(…)`, `requireActiveForSale()` |
| `domain/model` | `CustomerContact` | one record for `email` / `phone` / `address` (OQ-4) |
| `domain/model` | `CustomerRef` | view ref `{ externalId, name, taxId }` for §7's `customer` object |
| `domain/model` | `CustomerPage` | `{ content, totalElements, page, size }`, mirrors `SalePage` |
| `domain/exception` | `CustomerNotFoundException`, `CustomerInactiveException`, `CustomerTaxIdAlreadyExistsException` | the three §8 codes (R-C2, R-C7) |
| `application/port/in` | `ManageCustomersUseCase` | `list · get · create · edit · disable · enable` (§7) |
| `application/port/out` | `CustomerRepositoryPort` | `findByExternalId · existsByTaxId · create · save · list` |
| `application/service` | `ManageCustomersService` | the six operations + `AuditWritePort` (R-C11) |
| `infrastructure/adapter/out/persistence/customer` | `CustomerJpaEntity`, `CustomerMapper`, `CustomerSpringDataRepository`, `CustomerPersistenceAdapter` | driven adapter |
| `infrastructure/adapter/in/web/customer` | `CustomerController` | the seven endpoints of §7 |

`domain/` imports no Spring and no Jakarta. `SalesExceptionHandler`'s
`basePackages = "…sales.infrastructure.adapter.in"` already covers `in/web/customer` — verified in the
file, not assumed.

**No `domain/service` class is added.** The two rules each have an owner already: *active-for-sale* is
one guard on the aggregate (`Customer.requireActiveForSale()`, R-C7); *tax-id uniqueness* is a port
question (`existsByTaxId`) asked by `ManageCustomersService` before the write (T-C6), exactly as
`catalog` asks `CategoryRepositoryPort.existsByNameIgnoringCase(key, excludingExternalId)`. A
`CustomerPolicy` would hold one `if` — the sub-framework the brief forbids.

## 2. Domain model

```
Customer(UUID externalId, CustomerName name, CustomerTaxId taxId,
         CustomerContact contact, boolean active, Instant createdAt, Instant updatedAt)
CustomerContact(String email, String phone, String address)
```

**`CustomerName` and `CustomerTaxId` are the existing `sales/domain/model` records, reused.** Read
from disk: `CustomerName` enforces non-null, stripped, non-blank, ≤150 — exactly S-1's
`name VARCHAR(150) NOT NULL` (R-C1). `CustomerTaxId` enforces ≤30 and normalises blank → `null` —
exactly S-1's `tax_id VARCHAR(30)`, and the precondition D-3's partial unique index needs. A second
record with identical invariants would be pure duplication.

`CustomerContact` validates `email ≤ 100`, `phone ≤ 50`, `address ≤ 255` (the S-1 widths) and strips
blanks to `null`. Three one-field records were rejected: three classes to carry one length check each,
and none of the three is ever passed alone.

`Customer` is immutable; `withDetails`, `disable`, `enable` return new instances and advance
`updatedAt` (the schema has no triggers, so `updated_at` is application-maintained, as `suppliers` and
`products` already do). `disable`/`enable` are idempotent — the `catalog` precedent.

## 3. Ports

**In — `ManageCustomersUseCase`.** Mutations take `AuthenticatedPrincipal actor` (they write an audit
row, R-C11); reads take none — the record is organisation-global (§6) with no branch dimension, so a
read cannot vary by caller. `catalog`'s R-16/D-7 split, reused.

```
CustomerPage list(CustomerQuery query)                     // R-C5
Customer     get(UUID externalId)                          // R-C3 → CustomerNotFoundException
Customer     create(actor, CreateCustomerCommand)          // R-C1, R-C2, R-C11
Customer     edit(actor, UUID, EditCustomerCommand)
Customer     disable(actor, UUID) / enable(actor, UUID)    // R-C4, D-6

CreateCustomerCommand / EditCustomerCommand(String name, taxId, email, phone, address)
CustomerQuery(String search, Boolean active, int page, int size, String sort)
```

`Boolean active` (null = every state), **not** `catalog`'s `ActiveFilter` — importing it would be a
`sales → catalog` edge failing `ModuleBoundariesTest` rule 3. `search` is one term matched
case-insensitively over `name` and `tax_id`; default sort `name` (R-C5). **No
`CustomerSalesHistoryUseCase` is created** — see §5.

**Out — `CustomerRepositoryPort`**: `findByExternalId`, `existsByTaxId(String taxId, UUID
excludingExternalId)`, `create(NewCustomer)`, `save(Customer)`, `list(CustomerFilter)`.
`AuditWritePort` is reused from `shared` unchanged. No other out-port is added.

## 4. Changes to sale registration (R-C6 … R-C10, T-C1)

`RegisterSaleCommand` gains a **last** component `UUID customerExternalId` (nullable). Both
controllers pass it; `ExternalSaleController` gets the identical optional body field and calls the
same use case with zero new logic (R-C10, P-07).

`RegisterSaleService.register` gains one block, **after** `SaleAccessPolicy.resolveRegistrationBranch`
and **before** the basket work, so an unknown or inactive customer fails before any price resolution
or lock is taken:

1. `customerExternalId == null` → `customerName` must be present, else `IllegalArgumentException` →
   `400 invalid_request` (R-C9). Walk-in path otherwise untouched (R-C10).
2. otherwise `customerRepository.findByExternalId(id)`, empty ⇒ `CustomerNotFoundException` (R-C8);
3. then `customer.requireActiveForSale()` ⇒ `CustomerInactiveException` (R-C7, D-4);
4. the snapshot is a **field assignment**, not a port hop: `NewSale` is built with `customer.name()`
   and `customer.taxId()`, and the body's `customerName` / `customerTaxId` are dropped on the floor
   (R-C9, RNF-SEC-05) — the treatment monetary totals already get.

`NewSale` gains `UUID customerExternalId`. Everything else — lock order, pricing, `applyMovement`, the
audit row — is unchanged: one indexed lookup per sale, never per item (RNF-PER-02), and the customer
row takes **no lock** (T-C2). `Sale` gains a nullable `UUID customerExternalId` that `Sale.cancel(…)`
carries through; `SaleDetail` and `SaleSummary` each gain a nullable `CustomerRef customer` beside the
untouched snapshot fields (§7).

**OQ-1 — `customer.name` is the record's *current* name, not the snapshot.** The batched row needed to
turn `customer_id` into an `external_id` already carries `name` and `tax_id`, so live values cost
nothing extra, and a `customer` object merely repeating `customerName` would be worthless — R-C6's
whole point is that the two may differ. Reversal: read them off the snapshot, two lines.

## 5. Per-customer history — no second query stack (R-C12)

`SaleListQuery` and `SaleFilter` each gain **one** component, `UUID customerExternalId`;
`QuerySalesService.list` forwards it and is otherwise untouched. `CustomerController` adds one method
for `GET /api/sales/customers/{externalId}/sales`: `manageCustomersUseCase.get(externalId)` first
(R-C14 — `404` before anything else), then `querySalesUseCase.list(actor, new SaleListQuery(status,
from, to, page, size, sort, externalId))`. **No new in-port, no new service, no new repository
method.** `SaleController.list` passes `null`.

**R-C13 is already enforced and is not re-implemented.** `QuerySalesService.list` computes
`callerBranch = actor.role() == Role.ADMIN ? null : actor.branchId()` into `SaleFilter`, and every
native query in `SaleSpringDataRepository` — `computeAggregates` included — carries
`(:branchId IS NULL OR branch_id = :branchId)`. The history path goes through that same method and
those same queries, so a `BRANCH_MANAGER`'s history and its aggregates are branch-scoped by
construction: the customer filter is an extra `AND` and cannot widen the branch predicate. R-C15 falls
out for free — `customer_id IS NULL` rows match no `:customerId`, and the predicate is inert when the
parameter is `null`, so `GET /api/sales` behaves exactly as today.

## 6. Persistence

**`CustomerJpaEntity`** maps `customers`: plain columns, Lombok `@Getter/@Setter/@NoArgsConstructor`,
no `@Version` (no `sales` entity has one), no relation to `SaleJpaEntity`. **`SaleJpaEntity` gains `@Column(name = "customer_id") private Long customerId;` — nullable, plain
`Long`, no `@ManyToOne`.** Not a preference: `branchId`, `userId` and `priceListId` on that same
entity are already plain `Long`s, with the class Javadoc stating the rule. A `@ManyToOne` would be the
entity's only association, would drag `customers` into every sale fetch plan, and would let a numeric
id escape through a getter chain. `SaleMapper.toNewEntity` sets it from the resolved id; `toDomain`
and `toSummary` carry the external id and the `CustomerRef` outward.

**`CustomerSpringDataRepository`** — derived `findByExternalId`; one native paged search with
`(:search IS NULL OR name ILIKE … OR tax_id ILIKE …) AND (:active IS NULL OR is_active = :active)`
plus its `countQuery`; one native `existsByTaxId` excluding an `external_id`. The `external_id → id`
resolution for the FK is **not** here — it belongs to the sale side, below.

**`CustomerPersistenceAdapter`** is the only class that sees a customer's numeric id and never returns
one — the `CategoryPersistenceAdapter` rule. `create` and `save` use `saveAndFlush` inside a
`try/catch (DataIntegrityViolationException)` rethrowing `CustomerTaxIdAlreadyExistsException` with a
message naming **neither** `uq_customers_tax_id` nor the value (§8 "must not leak"). The flush matters:
without it the violation surfaces at commit, outside the adapter, and reaches the client as a 500.

**`SaleReferenceSpringDataRepository` gains three native queries**, in the shape already used for
`branches` / `price_lists`: `findCustomerIdByExternalId(UUID) → Optional<Long>` (the FK write);
`findCustomerRefs(Collection<Long>) → List<CustomerRefRow{id, externalId, name, taxId}>`, the batch
`SalePersistenceAdapter.list` runs once per page, never once per row (RNF-PER-01); and
`findCustomerDescriptors(Collection<UUID>)` behind a new
`SaleReferencePort.findCustomers(Collection<UUID>) → Map<UUID, CustomerDescriptor>` for
`SaleDetailAssembler`. Routing view enrichment through `SaleReferencePort` — already the branch/user
descriptor port for exactly this purpose — keeps the assembler's signature unchanged, so
`VoidSaleService` and `RegisterSaleService` need no new constructor argument. Accepted micro-cost:
registration reads the customer twice, as an aggregate for R-C7 and as a descriptor for the response.

**`SalePersistenceAdapter`** — `create` resolves `customerExternalId → Long` when non-null; `list`
resolves the filter's the same way and falls back to a **sentinel id** when it resolves to nothing,
mirroring the existing `resolveBranchIdOrSentinel`, so a race between §5's `get` and the query yields
an empty page rather than every sale.

**`SaleSpringDataRepository`** — the five existing native statements
(`searchOrderByCreatedAt`, `searchOrderByTotalAmount`, both `countQuery`s and `computeAggregates`)
each gain the same predicate `AND (:customerId IS NULL OR customer_id = :customerId)` and the
`@Param("customerId") Long customerId`. No method is added.

## 7. Web and security

`CustomerController` at `/api/sales/customers`, seven methods, §7 verbatim: `POST`, `GET` (list),
`GET /{externalId}`, `PUT /{externalId}`, `PATCH /{externalId}/disable`, `PATCH /{externalId}/enable`,
`GET /{externalId}/sales`. **No `DELETE` mapping exists** (R-C4). Request/response records nested in
the controller, as `SaleController` does. Page size: its own private `resolveSize`, a copy of
`SaleController`'s three lines — default 20, max 100, out of range throws `IllegalArgumentException`
→ `400 invalid_request` (R-C0, DT-10, never clamped). A shared helper class for eight lines, and a
controller calling another controller's static, were both rejected.

`SalesExceptionHandler` gains three handlers in the existing file and envelope —
`CustomerNotFoundException` → 404, `CustomerInactiveException` → 409,
`CustomerTaxIdAlreadyExistsException` → 409. Every §8 code is then reachable; none is dead.

**`SecurityConfig` — ordering is the whole trap.** The existing block ends
`/api/sales/*/cancellation` → `/api/sales/**` → `authenticated()`. A customer matcher placed after
`/api/sales/**` is dead. The two new lines go **immediately before** the cancellation matcher, string
literals only, `hasAuthority` never `hasRole`:

```java
.requestMatchers(HttpMethod.PATCH, "/api/sales/customers/*/disable", "/api/sales/customers/*/enable").hasAuthority("ADMIN")
.requestMatchers("/api/sales/customers", "/api/sales/customers/**").authenticated()
```

Only deactivation/reactivation is `ADMIN`-only (D-5); every authenticated internal role may `GET`,
`POST` and `PUT` a customer — the specific `PATCH` matcher precedes the catch-all so it is not
shadowed. `/api/sales/*/cancellation` matches one path segment, so it cannot collide with
`/api/sales/customers/**`.

## 8. Schema, validator and ER document

`01-init-schema.sql` takes S-1, S-2, S-3 exactly as §3.1 writes them, `customers` **above**
`CREATE TABLE sales` (`:305`); `02-seed-data.sql` takes the `6 = customers` header line and three rows
per §3.2. No other schema edit — if one appears, stop and report.

`scripts/validar_esquema.sh`: line 78's `igual "20 tablas creadas" … "20"` becomes `"21"` (verified —
the file really says 20). A new section after the sales one adds four checks with the existing
`rechaza` / `acepta` / `igual` helpers: `rechaza` a second customer with the same non-null `tax_id`;
`acepta` two customers with `tax_id IS NULL`; `rechaza` `DELETE FROM customers` for a customer a sale
references (`ON DELETE RESTRICT`); `igual` the seeded active-customer count. Lines 122 and 136 stay
untouched — confirmed in §3.3.

`docs/diagrama_er.md` (Spanish) gains `customers` and the `sales.customer_id` FK (`0..1` on the
customer side) in all four representations: §1 description, §2 Mermaid, §3 PlantUML, and
`docs/diagrams/diagrama_er.{excalidraw,puml}`. **No new Mermaid is authored in this design file** —
the register flow is §4's six numbered steps, and an unrendered diagram is an unverified assertion.

## 9. Transaction boundaries — only what differs from the archived design

| Operation | Atomic | `AFTER_COMMIT` | Locks | Isolation |
| :--- | :--- | :--- | :--- | :--- |
| Register a sale with a customer | unchanged T-01 set **plus** the `customer_id` + name/tax snapshot, written in the same `INSERT` (T-C1) | nothing (T-C7) | none on `customers` (T-C2); sale locks unchanged | default `READ COMMITTED` |
| Create / edit / disable / enable a customer | the customer row **and** its `audit_logs` row, `branch_id = NULL` (T-C3, R-C11) | nothing | none | default |
| List / get / search / history | `readOnly = true` (T-C4) | — | none | default |

A concurrent customer edit during a sale is a benign race (T-C2) — both snapshots are correct
receipts. Nothing is idempotent by key (T-C5); the partial unique index makes a duplicate `POST`
idempotent-by-refusal.

## 10. Decisions taken here, and their reversal cost

| # | Decision | Reversal |
| :--- | :--- | :--- |
| OQ-1 | `customer.{name,taxId}` in responses are the **live** record, not the snapshot (§4) | two mapper lines |
| OQ-2 | History is a controller method over `QuerySalesUseCase.list`; **no** `CustomerSalesHistoryUseCase` (§5) | extract an in-port later; nothing to unwind |
| OQ-3 | `Boolean active` in `CustomerQuery`, not `catalog`'s `ActiveFilter` (§3) | none — the alternative is a boundary violation |
| OQ-4 | `CustomerName` / `CustomerTaxId` reused; `CustomerContact` groups the other three (§2) | split the record if the widths ever diverge |
| OQ-5 | View enrichment via `SaleReferencePort`, domain read via `CustomerRepositoryPort` (§6) | one constructor argument on the assembler |

**Rejected:** a `customers` module (D-1); `@ManyToOne` from `SaleJpaEntity` (§6); a dedicated history
query, repository method or aggregate (R-C12); a `CustomerPolicy` domain service (§1.1); a
`ROLE_`-prefixed or `hasRole` matcher; Flyway alongside `init-db/` (DT-01); clamping an oversized
page (DT-10).

## 11. Technical debt

**No new debt.** `DT-04` moves **Aceptada → Resuelta**, ficha pointing here; `OI-02` becomes partially
resolved, price-list segmentation still explicitly out of scope (§3.4). `DT-01` grows by one table and
one column, the accepted cost stated in §3.5. `DT-03` and `DT-05` are untouched.
