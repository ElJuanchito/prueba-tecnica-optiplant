# Verification Report — `add-sales-customers`

**Change**: `add-sales-customers` (archived on branch `chore/ep-05-05-s5-customers-archivo`)
**Mode**: full artifacts (contract + design + tasks), source inspection plus real execution evidence
**Verdict**: **PASS**

## 1. Task completeness

All 33 tasks in `tasks.md` are marked `[x]` (S1 1.1–1.23, S2 2.1–2.10); 0 pending. Implementation
merged to `main` via PR #40 (backend) and PR #41 (frontend); commits `7031018`, `b6e434c`,
`a13bfda`. Source inspection confirms every listed class exists with the described shape.

## 2. Real execution evidence

| Command | Exit | Result |
| :--- | :--- | :--- |
| `python3 scripts/validar_trazabilidad.py` | 0 | `trazabilidad íntegra` — 43 RF · 34 RNF · 17 RN · 39 CU · 12 DT |
| `./scripts/validar_esquema.sh` (real PostgreSQL 17 in Docker) | 0 | `34 comprobaciones correctas — esquema íntegro`; new section **H. Clientes y ventas** — seeded active customers = 2, partial unique on `tax_id` (two `NULL` accepted), `ON DELETE RESTRICT` blocks deleting a customer with sales; 21 tables asserted |
| `cd backend && ./mvnw verify` | 0 | **BUILD SUCCESS** — Surefire 422 unit tests, Failsafe 178 integration tests, 0 failures / 0 errors; `ModuleBoundariesTest` and `SharedIsFrameworkFreeTest` green |

New tests, all passing: `CustomerCrudIT` (6), `CustomerSaleAssociationIT` (3), `CustomerSalesHistoryIT` (5),
`CustomerTest` (8), `ManageCustomersServiceTest` (7), `RegisterSaleServiceTest` (+6), `QuerySalesServiceTest` (+4).

## 3. Spec compliance

All 16 behavioural rules `R-C0`..`R-C15` and 7 transactional guarantees `T-C1`..`T-C7` from the contract
are covered by tests that passed at runtime. Every `§8` error code (`invalid_request`,
`customer_not_found`, `customer_inactive`, `customer_tax_id_already_exists`) is reachable from a
controller path via `SalesExceptionHandler`.

## 4. Design coherence

| Design decision | Verified |
| :--- | :--- |
| Customer is a sub-domain **inside `sales`**, no eleventh module (D-1) | ✅ every class under `com.optiplant.inventory.sales.*`; `ModuleBoundariesTest.MODULOS` unchanged |
| No domain service (`CustomerPolicy` rejected) | ✅ guard on the aggregate (`Customer.requireActiveForSale()`), uniqueness via port (`existsByTaxId`) |
| No domain event (T-C7) | ✅ grep confirms zero `publishEvent` / `ApplicationEventPublisher` / `AFTER_COMMIT` / `@TransactionalEventListener` references |
| `customers` table **before** `CREATE TABLE sales` | ✅ `01-init-schema.sql:305` vs `:322` |
| `sales.customer_id BIGINT` NULLABLE `REFERENCES customers(id) ON DELETE RESTRICT`, after `price_list_id` | ✅ `01-init-schema.sql:329` |
| `idx_sales_customer ON sales(customer_id, created_at)`; partial `uq_customers_tax_id ... WHERE tax_id IS NOT NULL` | ✅ `:344`, `:320` |
| RBAC by **operative role** (D-5): create/edit/read open to all internal roles, disable/enable `ADMIN`-only | ✅ `SecurityConfig:119-122` — PATCH `/disable`/`/enable` → `hasAuthority("ADMIN")`, rest `authenticated()`, matcher placed before the `/api/sales/**` catch-all; `hasAuthority` only, no `ROLE_` prefix |
| History reuses `QuerySalesUseCase.list`, no parallel query stack | ✅ one controller method + one component on `SaleListQuery` / `SaleFilter`; branch isolation (R-C13) enforced by existing predicates |
| `domain/` free of Spring/Jakarta | ✅ `Customer.java` imports only `java.*` and its own package |
| Seed rows: hex-only UUIDs `60000000-…`, one with tax id, one null, one inactive; no existing row edited | ✅ `02-seed-data.sql:232-235` |

## Issues

- **CRITICAL**: none.
- **WARNING**: none.
- **SUGGESTION**:
  1. Task 1.22 (record the `EXPLAIN` plan proving `idx_sales_customer` is the access path in the PR body)
     is documentary and cannot be re-verified from repo state. The index exists, the history path routes
     through the existing repository native queries, and `validar_esquema.sh` + `CustomerSalesHistoryIT`
     are green, so the functional obligation (RNF-PER-01) is met.
  2. The verify executor could not persist this report to Engram (`mem_*` tools unavailable in that
     context); the orchestrator persisted it.

## Final verdict: **PASS**

All 33 tasks complete and consistent with code state. All three project gates green against real
infrastructure. All behavioural rules and transactional guarantees runtime-verified. Every design
decision followed. No `reviewGate` in scope (RDD kill switch off). Proceeded to archive under
ordinary policy.
