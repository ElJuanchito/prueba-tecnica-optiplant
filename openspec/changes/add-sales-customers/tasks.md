# Tasks — `add-sales-customers`

Two slices (contract §11). Order is domain → application → infrastructure → tests → verification.
Every task names its files and the rule it satisfies. `docs/` is Spanish; code and `openspec/` English.

## S1 — schema, domain, application, infrastructure, web, unit tests

- [x] **1.1** `backend/init-db/01-init-schema.sql`: add `CREATE TABLE customers` + its three indexes **above** `CREATE TABLE sales` (`:305`); add `customer_id BIGINT REFERENCES customers(id) ON DELETE RESTRICT` after `price_list_id`; add `idx_sales_customer`. Verify: DDL byte-matches contract §3.1 (S-1, S-2, S-3).
- [x] **1.2** `backend/init-db/02-seed-data.sql`: header line `6 = customers`; three rows (`6…-1` with tax id, `-2` without, `-3` inactive), hex-only UUIDs, no existing row edited (§3.2).
- [x] **1.3** `scripts/validar_esquema.sh`: line 78 `"20"` → `"21"`; new customers section with `rechaza` duplicate non-null `tax_id`, `acepta` two null `tax_id`, `rechaza` deleting a referenced customer, `igual` seeded active count (§11 S1, T-C6, RNF-INT-03).
- [x] **1.4** Run `./scripts/validar_esquema.sh` — must be green before any Java is written (`docker compose down -v` first; wait for `PostgreSQL init process complete`).
- [x] **1.5** `docs/diagrama_er.md` §1, §2 Mermaid, §3 PlantUML + `docs/diagrams/diagrama_er.{excalidraw,puml}` — `customers` and the `0..1` `sales.customer_id` FK (design §8).
- [x] **1.6** Domain: `sales/domain/model/{Customer,CustomerContact,CustomerRef,CustomerPage}.java`, reusing the existing `CustomerName` / `CustomerTaxId` (R-C1, R-C4, R-C7; design §2). Verify: no Spring, no Jakarta import.
- [x] **1.7** Domain exceptions: `sales/domain/exception/{CustomerNotFoundException,CustomerInactiveException,CustomerTaxIdAlreadyExistsException}.java` (§8).
- [x] **1.8** Ports: `application/port/in/ManageCustomersUseCase.java` (6 operations + 3 command records), `application/port/out/CustomerRepositoryPort.java` (design §3). R-C1…R-C5.
- [x] **1.9** `application/service/ManageCustomersService.java` — `@Transactional`, reads `readOnly`; `existsByTaxId` guard before every write (R-C2, T-C6); one `AuditWritePort` row per mutation, `entity_name = 'CUSTOMER'`, `branchId = null` (R-C11, T-C3).
- [x] **1.10** Persistence: `infrastructure/adapter/out/persistence/customer/{CustomerJpaEntity,CustomerMapper,CustomerSpringDataRepository,CustomerPersistenceAdapter}.java`; `saveAndFlush` + `DataIntegrityViolationException` → domain exception, no constraint name leaked (design §6, §8).
- [x] **1.11** Modify `sales/domain/model/Sale.java` (+ `customerExternalId`, carried by `cancel`), `SaleDetail.java`, `SaleSummary.java` (+ `CustomerRef customer`) (R-C6, §7).
- [x] **1.12** Modify `application/port/in/RegisterSaleUseCase.java` (`RegisterSaleCommand` + `customerExternalId`) and `application/port/out/SaleRepositoryPort.java` (`NewSale` + `customerExternalId`, `SaleFilter` + `customerExternalId`) (R-C6, R-C12).
- [x] **1.13** Modify `application/service/RegisterSaleService.java` — the four-step block of design §4: R-C9 either/or, R-C8 not-found, R-C7 inactive, snapshot copy inside the existing single transaction (T-C1). Body `customerName`/`customerTaxId` ignored when the record wins.
- [x] **1.14** Modify `application/port/out/SaleReferencePort.java` (+ `findCustomers`, `CustomerDescriptor`) and `application/service/SaleDetailAssembler.java` (build `CustomerRef`) — assembler signature unchanged (design §6).
- [x] **1.15** Modify `application/service/QuerySalesService.java` — forward `customerExternalId` into `SaleFilter`; branch scoping untouched (R-C12, R-C13).
- [x] **1.16** Modify `infrastructure/adapter/out/persistence/{SaleJpaEntity,SaleMapper,SaleSpringDataRepository,SalePersistenceAdapter,SaleReferenceAdapter,SaleReferenceSpringDataRepository}.java` — plain `Long customerId`, the `AND (:customerId IS NULL OR customer_id = :customerId)` predicate in all five native statements, sentinel resolution, batched ref lookup (design §6, R-C15, RNF-PER-01).
- [x] **1.17** Web: `infrastructure/adapter/in/web/customer/CustomerController.java` — the seven §7 endpoints, no `DELETE`, own `resolveSize` rejecting out-of-range with `400 invalid_request` (R-C0, R-C4, R-C5, DT-10).
- [x] **1.18** Modify `infrastructure/adapter/in/web/{SaleController,ExternalSaleController}.java` — optional `customerExternalId` in both request bodies, `customer` in both responses; external path runs the same use case with zero new logic (R-C10).
- [x] **1.19** Modify `infrastructure/adapter/in/web/SalesExceptionHandler.java` — the three new codes (§8). Verify: every §8 code reachable from a controller path.
- [x] **1.20** Modify `iam/infrastructure/config/SecurityConfig.java` — the two matchers **before** `/api/sales/*/cancellation`, `hasAuthority` only (design §7, §6).
- [x] **1.21** Unit `*Test` (no Docker): `CustomerTest`, `ManageCustomersServiceTest`, `RegisterSaleServiceTest` (extend), `QuerySalesServiceTest` (extend) covering R-C1, R-C2, R-C6 (a later name edit leaves a built sale unchanged), R-C7, R-C9's three combinations, R-C13's `ADMIN`-vs-branch decision (§11 S1).
- [x] **1.22** `EXPLAIN` the history query against a seeded database and confirm `idx_sales_customer` is the access path (S-3, RNF-PER-01). Record the plan in the PR body.
- [x] **1.23** `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

## S2 — integration tests and documentary close

- [ ] **2.1** `CustomerSaleAssociationIT` — R-C6 / T-C1: FK **and** snapshot written together; editing the customer afterwards leaves the stored receipt unchanged (RNF-INT-01).
- [ ] **2.2** `CustomerSalesHistoryIT` — R-C12 / R-C13 / R-C14 / R-C15: branch A sees only its own sales and matching aggregates; `ADMIN` sees both; unknown customer `404`; customer with no sales `200` empty; a null-`customer_id` sale appears in `GET /api/sales` and in no history.
- [ ] **2.3** `CustomerCrudIT` — R-C2 / R-C4 / R-C7 / §6: duplicate tax id `409`; two null tax ids accepted; `OPERATOR` refused a write, allowed a read; disabled customer refused on a new sale but keeps their history; no `DELETE` route.
- [ ] **2.4** `docs/especificacion_requerimientos.md`: `RF-VEN-06` in the `RF` table **and** in the Should priority table (§4).
- [ ] **2.5** `docs/casos_de_uso.md`: `CU-VEN-05`, `CU-VEN-06` in the catalogue; §6 matrix row `| RF-VEN-06 | CU-VEN-05, CU-VEN-06, CU-VEN-01 |`; §2.3 «Gestionar clientes» row `✅ ❌ ❌ ❌`; line 199 `CU-VEN-01 .. CU-VEN-04` → `.. CU-VEN-06`.
- [ ] **2.6** `docs/historias_de_usuario.md`: `HU-VEN-05`. `docs/diagrams/casos_de_uso_04_ventas.excalidraw`: the two use cases.
- [ ] **2.7** `docs/deuda_tecnica.md`: DT-04 **Aceptada → Resuelta**, ficha pointing at this change; OI-02 (SRS §5) partially resolved, price-list segmentation still out of scope. Neither cited by a retired id in any changelog line (§3.4).
- [ ] **2.8** `openspec/PLAN.md`: `sales` row gains `CU-VEN-05`, `CU-VEN-06`.
- [ ] **2.9** `python3 scripts/validar_trazabilidad.py` green, reporting **43 RF · 34 RNF · 17 RN · 39 CU · 12 DT**.
- [ ] **2.10** `./scripts/validar_esquema.sh` green; `cd backend && ./mvnw verify` green.

**PR boundary:** S1 and S2 are one chained PR each — S1 is not mergeable without S2's documentary
close, but is independently reviewable. Docker-requiring tests end in `IT`; never `Test`.
