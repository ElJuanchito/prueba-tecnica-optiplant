# Contract — `add-sales-customers`

Acceptance contract for **customer records inside the `sales` module**.
Step 1 of the cycle: `backend-module-designer` consumes this file next.

Sources are cited by identifier, never restated. Read `docs/especificacion_requerimientos.md` §1.3 and
§5 (OI-02), `docs/casos_de_uso.md` §2.3 and §6, `docs/deuda_tecnica.md` DT-04 and DT-10, and the
archived `.../archive/2026-08-30-add-sales-module/contract.md`, which this contract **extends**
rather than restates.

---

## 1. Scope

A **customer record** — a stored party a sale can be attributed to. It is a record, **not a
principal**: no credentials, no `users` row, no login, no authority. Its entire reason to exist is to
serve `sales` in three ways, and nothing else: (a) CRUD administration of the record, (b) an
**optional** association at sale registration, (c) a **per-customer sales history** query.

**Out of scope — explicit, and the design MUST NOT absorb any of it.** Authentication, login, roles or
a `users` row for a customer; price-list segmentation by customer (OI-02 stays *partially* open, §3.4);
credit limits, accounts receivable, customer balances or payment terms; an address table or multiple
addresses per customer; a contacts sub-entity; customer categories, segments or tags; loyalty schemes
or points; merge/dedupe tooling; bulk import; physical deletion or any soft-delete mechanism beyond
the `is_active` flag; per-customer discounts; branch scoping of the customer record; domain events of
any kind; a customer-facing portal; the frontend. Making a customer **mandatory** on a sale is also out
of scope — see D-2.

**Blocker check: none found.** Every requirement below is satisfied by CRUD + association + history.
Nothing encountered while writing this contract forces a scope increase.

---

## 2. Affected modules

**`sales` only.** `Customer` is a **sub-domain inside `sales`**, not an eleventh module:
`sales/domain/model/Customer*`, `sales/application/port/in|out/…`,
`sales/infrastructure/adapter/{in/web,out/persistence}/customer/…`.

| From | To | Via | Direction |
| :--- | :--- | :--- | :--- |
| `sales` | `shared` | `AuthenticatedPrincipal`, `Role`, `AuditWritePort` — all already consumed | one-way |
| anything | `sales` customer types | **nothing** — no other module reads or writes `customers` | none |

No new cross-module edge, therefore **no cycle**, and `shared` keeps importing no module name.
`ModuleBoundariesTest.MODULOS` is unchanged, `docs/decisiones_arquitectura_tecnica.md` §2.4 gains **no
row**, and its §5 package tree is followed as written.

**D-1 — no new module.** A customer exists only to be named on a sale and is written and read by
`sales` alone, so a boundary between them would separate nothing. An eleventh module would instead
rewrite §2.4, §5, every document that says *«los diez módulos»*,
`docs/diagrams/arquitectura_02_modulos.excalidraw` and `ModuleBoundariesTest.MODULOS`. Reversal: a
package move plus those edits — mechanical but wide, and still far cheaper than the reverse direction.

---

## 3. Schema delta

This change **does** modify `backend/init-db/01-init-schema.sql` and `02-seed-data.sql` — the
`add-catalog-module` precedent (§1.2 of its contract). Verified against the current DDL, not assumed.

### 3.1. `01-init-schema.sql`

| # | Edit | Rationale |
| :--- | :--- | :--- |
| **S-1** | **New table `customers`**, placed in section 5 (Ventas) **immediately before `CREATE TABLE sales`** (`01-init-schema.sql:305`) so the FK in S-2 resolves. | Column widths mirror `suppliers` (`:248-260`) — the schema's existing "external party" shape — so no new convention is invented. |
| **S-2** | `sales`: add `customer_id BIGINT REFERENCES customers(id) ON DELETE RESTRICT` (**NULLABLE**), after `price_list_id`. | `RESTRICT`, like every other FK on `sales`: a sale must never vanish with a customer (RN-12). Nullable — see D-2. |
| **S-3** | `CREATE INDEX idx_sales_customer ON sales(customer_id, created_at);` | The access path for CU-VEN-06 (RNF-PER-01). The designer MUST confirm with `EXPLAIN` that the history query uses it. |

```sql
CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(30),                       -- NIT / RUC; optional (D-3)
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_external_id ON customers(external_id);
CREATE INDEX idx_customers_name ON customers(name);
CREATE UNIQUE INDEX uq_customers_tax_id ON customers(tax_id) WHERE tax_id IS NOT NULL;
```

No `contact_name` — that is a *supplier* concept, not a customer's. There are **no triggers anywhere in
this schema**, so `updated_at` is application-maintained, as `suppliers` and `products` already do.

**D-3 — `tax_id` is optional but unique when present, via a partial unique index.** DT-04's stated harm
is *«el mismo cliente puede quedar escrito de varias formas distintas»*; a unique tax id is the only
cheap defence against that. It cannot be `NOT NULL UNIQUE` like `suppliers.tax_id`, because a walk-in
customer has no tax id and `sales.customer_tax_id` is already nullable. The partial unique index is the
established technique in this schema (`uq_price_lists_single_default ON price_lists(is_default) WHERE
is_default`, `:145`). Name is **not** unique — homonyms are legitimate. Reversal: drop the index.

### 3.2. `02-seed-data.sql`

- Header convention gains one line: **`6 = customers`**. Digits `a b c d e f 1 2 3 4 5` are taken;
  `6` is free and hexadecimal (the invariant that broke the seed once already).
- Three rows, literals shaped `'60000000-0000-0000-0000-00000000000N'`, matching `price_lists`
  (`02-seed-data.sql:70-73`): one with a tax id, one without (proving D-3's partial index), one
  inactive (proving R-C7 and giving the ITs a fixture).
- **No existing seed row changes — verified, not assumed.** `02-seed-data.sql` contains **no `INSERT
  INTO sales`** at all, so S-2 adds a nullable column nothing has to fill.

### 3.3. `scripts/validar_esquema.sh` — this change makes it fail unless updated

**Verified by running:** `01-init-schema.sql` defines exactly 20 tables and line 78 asserts
`igual "20 tablas creadas" … "20"`. S-1 makes it **21**. That literal MUST be updated in S1 or the
gate goes red. Two neighbouring checks were confirmed to survive untouched: line 136 (every table has
an `external_id` — `customers` has one) and line 122 (a direct `INSERT INTO sales` with an explicit
column list omitting `customer_id` — still valid because S-2 is nullable).

### 3.4. DT-04 and OI-02

- **DT-04** *(«Cliente sin entidad propia en las ventas», `docs/deuda_tecnica.md:44,185`)* moves from
  **Aceptada** to **Resuelta**, its ficha rewritten to point at this change. `validar_trazabilidad.py`
  only matches `**DT-NN**` and `### DT-NN` (`:83-84`), so the status word is free text and the count
  stays 12. Cite DT-04 **by its title** in any changelog line, never by a resurrected id.
- **OI-02** (SRS §5, `:336`) is **partially** resolved: the customer entity now exists, so the
  denormalisation objection is answered. **Price-list segmentation by customer remains out of scope**
  and OI-02's ficha MUST say so — SRS §1.3's spirit, and no `RF` asks for it.

### 3.5. DT-01 friction

`backend/init-db/` only runs against an empty volume, so an initialised environment needs
`docker compose down -v` to pick S-1…S-3 up. **Flyway MUST NOT be added** alongside `init-db/`
(CLAUDE.md; DT-01 is a replacement, not a coexistence). One table and one column make DT-01 marginally
larger — the accepted cost of not carrying DT-04 forward.

---

## 4. Traceability

Existing identifiers end at RF-VEN-05 / CU-VEN-04 / HU-VEN-04. Baseline verified green while writing
this contract: **42 RF · 34 RNF · 17 RN · 37 CU · 12 DT**.

| RF / RNF / RN | CU | HU |
| :--- | :--- | :--- |
| **RF-VEN-06** *(new — customer management)* | **CU-VEN-05** *(administer customers)*, **CU-VEN-06** *(query a customer's purchase history)*, CU-VEN-01 *(the optional association at sale time)* | **HU-VEN-05** *(new)* |
| RF-VEN-01, RF-VEN-04 *(the receipt now carries an identified customer)* | CU-VEN-01, CU-VEN-04 | HU-VEN-01, HU-VEN-04 |
| RF-VAL-02 *(audit of the mutations)* | CU-VEN-05 → CU-SEG-04 | HU-INV-04 |
| RN-12 *(no physical deletion)*, RN-14 *(branch from the session)* | constrain the above | — |

**New identifiers are declared here and written in S2**, in `docs/` (Spanish), all four places or the
validator fails: the SRS `RF` table **and** its Must/Should priority table (RF-VEN-06 is **Should**,
alongside RF-VEN-03 and RF-VEN-05); the `docs/casos_de_uso.md` catalogue; its §6 matrix row
`| RF-VEN-06 | CU-VEN-05, CU-VEN-06, CU-VEN-01 |`; and `docs/historias_de_usuario.md`. Three further
edits found by inspection, easy to miss: the §2.3 RBAC matrix gains a **«Gestionar clientes»** row; the
module map at `docs/casos_de_uso.md:199` reads `CU-VEN-01 .. CU-VEN-04` and must become `.. CU-VEN-06`;
and `docs/diagrams/casos_de_uso_04_ventas.excalidraw` gains the two use cases. Expected after S2:
**43 RF · 34 RNF · 17 RN · 39 CU · 12 DT**.

---

## 5. Behavioural contract

**R-C0** Inherited unchanged from the archived contract: page-size **rejection** with
`400 invalid_request` (R-00, DT-10 — never `catalog`'s silent clamp), `external_id` only on the wire,
`hasAuthority()` never `hasRole()`, and every rule R-01 … R-29 that this change does not name.

### Administer customers (CU-VEN-05, RF-VEN-06)

- **R-C1** A customer MUST persist with a non-blank `name` (≤150) and optional `taxId`, `email`, `phone`, `address`, active on creation. *Given* a blank name, *then* `400 invalid_request`.
- **R-C2** *Given* a `taxId` already held by another customer, *when* creating or editing, *then* `409 customer_tax_id_already_exists`. *Given* two customers with no `taxId`, *then* both persist (D-3).
- **R-C3** Editing MUST NOT change `externalId`, `createdAt` or the identity of any past sale (R-C6). *Given* an unknown `externalId`, *then* `404 customer_not_found`.
- **R-C4** Deactivation is the **only** removal: `is_active` false. *Given* a `DELETE` request, *then* the route does not exist. *Given* a deactivated customer, *when* their past sales are queried, *then* they are returned in full (RN-12).
- **R-C5** The listing MUST be paginated, filterable by `active` and by a `search` term over name and tax id, sorted by name by default. Boundary: `size` above the cap is rejected, not clamped (R-C0).
- **R-C11** Create, edit, deactivate and reactivate MUST each write one `audit_logs` entry, `entity_name = 'CUSTOMER'`, `entity_id` = the customer's `external_id`, in the **same transaction** (RF-VAL-02). Verified: `audit_logs.entity_name` is a plain `VARCHAR(50)` with **no `CHECK`** (`01-init-schema.sql:441`), so no schema edit is required for the new value.

### Associate a customer with a sale (CU-VEN-01, RF-VEN-06)

- **R-C6** `POST /api/sales` gains an **optional** `customerExternalId`. *Given* it is supplied, *then* in the **same transaction** as the sale the backend MUST set `sales.customer_id` **and copy that customer's current `name` and `taxId` into `sales.customer_name` / `sales.customer_tax_id`**. *Given* the customer's name is edited afterwards, *when* the receipt is read again, *then* it shows the name as it was at sale time — the frozen-snapshot principle the frozen price already follows (R-12, DT-05).
- **R-C7** *Given* a `customerExternalId` naming an **inactive** customer, *then* `409 customer_inactive` and nothing is persisted (D-4).
- **R-C8** *Given* a `customerExternalId` naming nothing, *then* `404 customer_not_found` and nothing is persisted.
- **R-C9** The request MUST carry `customerExternalId` **or** `customerName`. *Given* neither, *then* `400 invalid_request` — `sales.customer_name` stays `NOT NULL` and no schema edit touches it. *Given* both, *then* the customer record wins and the body's `customerName` / `customerTaxId` are **ignored, never trusted**, exactly as monetary totals already are (R-14, RNF-SEC-05).
- **R-C10** The external POS intake (`POST /api/external/sales`, CU-EXT-02) accepts the same optional field and runs the **same** use case with **zero** new logic (P-07). *Given* it is omitted, *then* the walk-in path of R-01 is unchanged — every existing sale flow keeps working untouched.

### Per-customer purchase history (CU-VEN-06, RF-VEN-06)

- **R-C12** `GET /api/sales/customers/{externalId}/sales` MUST reuse the **existing** `QuerySalesUseCase.list` machinery with a `customerExternalId` added to `SaleListQuery` / `SaleFilter`. No parallel query stack, no second repository path, no new aggregate: the existing `aggregates { salesCount, totalAmount }` **is** the purchase summary.
- **R-C13** Branch isolation is unchanged and is the sharpest rule here: the customer record is organisation-global, **their history is not**. *Given* a `BRANCH_MANAGER` of branch A, *then* the response contains only branch A's sales with that customer, and the aggregates cover only those (RN-14, RNF-SEC-03, R-25). `ADMIN` reads network-wide.
- **R-C14** *Given* an unknown customer, *then* `404 customer_not_found` — the reason this is a sub-resource rather than a flat filter on `GET /api/sales`, which could not distinguish "no sales" from "no such customer". *Given* a known customer with no sales in the caller's scope, *then* `200` with an empty page and zeroed aggregates, never `404`.
- **R-C15** Sales registered **before** this change carry `customer_id = NULL` and MUST NOT appear in any customer's history, nor break the listing. *Given* the whole `sales` table has a null `customer_id`, *then* `GET /api/sales` behaves exactly as today.

---

## 6. Authorization matrix

Extends §5 of the archived sales contract; cross-checked against `docs/casos_de_uso.md` §2.3, whose
nearest analogue for organisation-global master data is *«Gestionar catálogo maestro»* — `ADMIN` only.
Enforced with `hasAuthority()`.

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | External | Branch rule |
| :--- | :---: | :---: | :---: | :---: | :--- |
| Create / edit customer (CU-VEN-05) | ✅ | ❌ | ❌ | ❌ | none — organisation-global, like `price_lists` |
| Deactivate / reactivate customer | ✅ | ❌ | ❌ | ❌ | none |
| Read / list / search customers | ✅ | ✅ | ✅ | ❌ | none — every seller must find the customer to bill |
| Associate a customer with a sale (CU-VEN-01) | ✅\* | ✅ | ✅ | ✅ | the **sale's** branch is still session-derived (RN-14); the customer carries no branch |
| Customer purchase history (CU-VEN-06) | ✅ | ✅ | ✅ | ❌ | own branch; `ADMIN` network-wide (R-C13) |

**\*** A corporate `ADMIN` still cannot register a sale — `403 branch_context_required` (§5 of the
archived contract) — and this change does not soften it. **No endpoint here accepts a branch** in path,
query or body. The new §2.3 row *«Gestionar clientes»* is `✅ ❌ ❌ ❌`.

**D-5 — writes are `ADMIN`-only, reads open to every authenticated role.** Identical reasoning to
PA-03 for price administration: §2.3 has no row for it, the nearest analogue is `ADMIN`-only, and an
`OPERATOR` who cannot look a customer up cannot bill one. Reversal: one security matcher.

---

## 7. API surface

All identifiers are `external_id` UUIDs (RNF-API-02). Page envelope and error envelope are the
existing ones: `{ content, totalElements, page, size }` and `{ code, message }`. Page size limits and
the rejection behaviour match `SaleController` (default 20, max 100, out-of-range → `400`).

| Method | Path | Purpose | Request | Response |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/sales/customers` | CU-VEN-05 | `{ name, taxId?, email?, phone?, address? }` | `201` customer |
| `GET` | `/api/sales/customers` | CU-VEN-05 | `active?`, `search?`, `page`, `size`, `sort` | page of customers |
| `GET` | `/api/sales/customers/{externalId}` | CU-VEN-05 | — | `200` customer |
| `PUT` | `/api/sales/customers/{externalId}` | CU-VEN-05 | `{ name, taxId?, email?, phone?, address? }` | `200` customer |
| `PATCH` | `/api/sales/customers/{externalId}/disable` | R-C4 | — | `200` customer |
| `PATCH` | `/api/sales/customers/{externalId}/enable` | R-C4 | — | `200` customer |
| `GET` | `/api/sales/customers/{externalId}/sales` | CU-VEN-06 | `status?`, `from?`, `to?`, `page`, `size`, `sort` | the **existing** sale page envelope + `aggregates` |

Customer resource: `{ externalId, name, taxId, email, phone, address, active, createdAt, updatedAt }`.
`POST /api/sales` and `POST /api/external/sales` gain one optional field, `customerExternalId`;
`SaleDetailResponse` and `SaleSummaryResponse` gain one optional object,
`customer: { externalId, name, taxId } | null`, **beside** the existing `customerName` /
`customerTaxId` snapshot fields, which stay and are never removed — they are what the receipt says
(R-C6). No numeric `id` appears in any field, message or `Location` header.

**D-6 — `disable` / `enable` rather than a single toggle.** It is the shape `catalog` already ships
(`ProductController:122,128`, `CategoryController:92,98`), the closest analogue in kind. Reversal: one
endpoint.

---

## 8. Error taxonomy

Extends §7 of the archived contract. New codes only:

| Code | HTTP | Raised when |
| :--- | :---: | :--- |
| `invalid_request` *(existing)* | 400 | blank/oversized name, malformed UUID, page size out of range, neither `customerExternalId` nor `customerName` on a sale (R-C9) |
| `customer_not_found` | 404 | unknown customer on read, edit, disable/enable, sale association (R-C8) or history (R-C14) |
| `customer_inactive` | 409 | R-C7 — associating a deactivated customer with a new sale |
| `customer_tax_id_already_exists` | 409 | R-C2 — a second customer with the same non-null `tax_id` |

**Must not leak.** Whether a tax id belongs to an existing customer, other than through the `409` a
writer already earned by holding `ADMIN`; another branch's sales in a history response, or their
existence in its aggregates (R-C13); numeric `id` values anywhere; stack traces, SQL text, constraint
names such as `uq_customers_tax_id`, or JPA exception messages. Everything §7 of the archived contract
forbids stays forbidden.

**D-4 — associating an inactive customer is refused, not silently allowed.** `is_active` is the only
removal this contract has (R-C4); if a deactivated customer could still accrue sales, deactivation
would mean nothing. Past sales stay fully readable and the history endpoint keeps working for an
inactive customer — deactivation blocks *new* attribution, not the record. Reversal: delete one domain
check.

---

## 9. Transactional and consistency guarantees

- **T-C1** The customer snapshot copy is **atomic with the sale**: `customer_id`, `customer_name` and `customer_tax_id` are written in the same `INSERT`, inside the same transaction that writes `sale_items`, calls `applyMovement` and writes the audit entry (T-01, RN-02). There is no second write, no port hop and **no event** — the copy is a field assignment before the insert.
- **T-C2** The customer is read inside that transaction; its row takes **no lock**. Two concurrent sales for one customer never contend, and a concurrent edit of the customer is a benign race whose only effect is which snapshot a sale freezes — both are correct receipts (D-4 reasoning, R-C6).
- **T-C3** Customer CRUD is its own short transaction, together with its `audit_logs` entry (R-C11). `audit_logs.branch_id` is **NULL**: the mutated resource is organisation-global, the resolution T-03 already took for price lists.
- **T-C4** Reads — list, detail, search, history — are `readOnly` and take no lock (RN-09, T-05).
- **T-C5** Nothing here is idempotent by key and none is added: two identical `POST /api/sales/customers` produce two customers unless `tax_id` collides, in which case the partial unique index makes the second **idempotent-by-refusal** (`409`, R-C2).
- **T-C6** Database constraints are the last line of defence, never the first (RNF-INT-03): the domain refuses before the write for `uq_customers_tax_id`, for `name NOT NULL`, and for the `ON DELETE RESTRICT` on `sales.customer_id`.
- **T-C7** **Nothing is published `AFTER_COMMIT`.** No domain event exists in this change (§1).

---

## 10. Non-functional obligations

| Obligation | Target | How it is measured |
| :--- | :--- | :--- |
| RNF-PER-01 | p95 < 200 ms for customer list, detail and history | `idx_customers_external_id`, `idx_customers_name`, `idx_sales_customer` MUST be the access paths, proven with `EXPLAIN`; no N+1 when a sale page resolves its customers |
| RNF-PER-02 | sale registration stays < 500 ms | R-C6 adds **one** indexed lookup by `external_id` per sale, never one per item |
| RNF-PER-04 | every collection paginated, oversized page rejected | `400 invalid_request` (R-C0, R-C5, DT-10) |
| RNF-INT-01 | the snapshot cannot desynchronise from the sale | T-C1, proven by an IT |
| RNF-INT-03 | uniqueness guaranteed by the database, not only the domain | `uq_customers_tax_id`, asserted in `validar_esquema.sh` (§11) |
| RNF-SEC-01 | `hasAuthority()`, no `ROLE_` prefix | §6 matchers plus method-level checks |
| RNF-SEC-03 | history is branch-isolated although the customer is global | R-C13, proven by an IT |
| RNF-SEC-05 | all input validated in the backend | bean validation plus domain value objects; body `customerName` ignored when the record wins (R-C9) |
| RNF-API-01, RNF-API-02 | OpenAPI documents all seven operations; only `external_id` on the wire | `/v3/api-docs`; §7, asserted on response shape |
| RNF-OBS-01 | structured logs carry correlation id, user, operation | customer mutations logged; no tax id in logs |

---

## 11. Definition of done

Two slices (`openspec/PLAN.md`). Every item is a command to run or a file to open.

### S1 — schema, domain, application, infrastructure, web, unit tests

- [ ] `01-init-schema.sql` carries S-1, S-2, S-3 exactly as §3.1 writes them, `customers` **above** `CREATE TABLE sales`.
- [ ] `02-seed-data.sql` header lists `6 = customers`; three rows with hex-only UUIDs; **no existing row edited**.
- [ ] `scripts/validar_esquema.sh:78` reads `"21"`, and a new customers section adds: `rechaza` a second customer with the same non-null `tax_id`; `acepta` two customers with `tax_id IS NULL`; `rechaza` deleting a customer referenced by a sale (`ON DELETE RESTRICT`); `igual` the seeded active-customer count.
- [ ] `./scripts/validar_esquema.sh` green — **mandatory now**, `backend/init-db/` changed.
- [ ] `docs/diagrama_er.md` updated in **all four** representations: §1 description, §2 Mermaid, §3 PlantUML, and `docs/diagrams/diagrama_er.{excalidraw,puml}`.
- [ ] `com.optiplant.inventory.sales.domain.model.Customer*` exists with no Spring or Jakarta import; no new class in a direct subpackage of `com.optiplant.inventory`; no new module package.
- [ ] Controller, adapters and `SecurityConfig` matchers for `/api/sales/customers/**` using `hasAuthority()` (§6); the history endpoint delegates to `QuerySalesUseCase.list` — grep proves no second repository query path (R-C12).
- [ ] Unit `*Test` (no Docker) covering: R-C1 name and field validation; R-C2 tax-id uniqueness at the domain level; R-C6 the snapshot copy, including that a later name edit does not alter a built sale; R-C7 the inactive refusal; R-C9 all three combinations of `customerExternalId` / `customerName`; R-C13 the branch-scoping decision for `ADMIN` vs the other roles.
- [ ] Every §8 code is reachable from at least one controller path — no dead error code.
- [ ] `cd backend && ./mvnw verify` green, `ModuleBoundariesTest` included.

### S2 — integration tests and documentary close

- [ ] `CustomerSaleAssociationIT` — R-C6/T-C1: a sale with `customerExternalId` stores the FK **and** the snapshot; editing the customer afterwards leaves the stored receipt unchanged.
- [ ] `CustomerSalesHistoryIT` — R-C12/R-C13/R-C14/R-C15: branch A sees only its own sales with the customer and matching aggregates; `ADMIN` sees both branches; an unknown customer is `404`; a customer with no sales is an empty `200`; a pre-existing null-`customer_id` sale appears in `GET /api/sales` and in no history.
- [ ] `CustomerCrudIT` — R-C2/R-C4/R-C7/§6: duplicate tax id `409`; two null tax ids accepted; `OPERATOR` refused a write and allowed a read; a disabled customer is refused on a new sale but keeps their history; no `DELETE` route exists.
- [ ] `docs/` (Spanish) closed: RF-VEN-06 in the SRS `RF` table **and** its Should priority row; CU-VEN-05 and CU-VEN-06 in the catalogue; the §6 matrix row; HU-VEN-05; the §2.3 «Gestionar clientes» row; `casos_de_uso.md:199` reading `CU-VEN-01 .. CU-VEN-06`; `docs/diagrams/casos_de_uso_04_ventas.excalidraw` updated.
- [ ] DT-04 marked **Resuelta** with its ficha pointing at this change; OI-02 marked partially resolved, price-list segmentation still out of scope (§3.4). Neither cited by a retired id in any changelog line.
- [ ] `python3 scripts/validar_trazabilidad.py` green, reporting **43 RF · 34 RNF · 17 RN · 39 CU · 12 DT**.
- [ ] `./scripts/validar_esquema.sh` green; `cd backend && ./mvnw verify` green.

---

## 12. Open questions

**None blocking.** Six decisions were taken here rather than escalated — D-1 (no new module), D-2, D-3
(optional-but-unique tax id), D-4 (inactive customers refused on new sales), D-5 (`ADMIN`-only writes),
D-6 (`disable`/`enable`) — each with its reversal cost stated in place.

- **D-2 — `sales.customer_id` is NULLABLE and the association stays optional.** Making it mandatory
  would break every existing flow at once: `CU-VEN-01`'s walk-in sale, `CU-EXT-02`'s external POS
  intake (whose payload has no customer record to point at), the existing seed and test corpus, and
  R-01 as already shipped. Reversal: a backfill plus `SET NOT NULL` — cheap **in this direction**,
  expensive in the other, which is why nullable is the correct default now.
