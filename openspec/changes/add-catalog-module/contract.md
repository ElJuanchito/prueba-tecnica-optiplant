# Acceptance Contract — `add-catalog-module`

- **Module**: `catalog` (new) — products, categories and units of measure (arch. §2.4, `docs/decisiones_arquitectura_tecnica.md:79`).
- **Use cases**: CU-INV-01, CU-INV-02.
- **Branch**: `feat/ep-02-catalog-01-contrato`.
- **Phase**: 1 of 3. Next is `backend-module-designer`, which consumes this file and produces `design.md` + `tasks.md`.
- **Language**: English, aligned with `openspec/config.yaml:9` and the archived `add-iam-module`. `docs/` stays Spanish and is not touched by this change.

> All seven open questions from the first revision were resolved by the user. Their
> resolutions are recorded in §12 together with the consequences each one pulled in. Two
> of them (PA-04, PA-05) turn this into a change that **modifies the database schema**,
> and one (PA-02) forces a cross-module port that did not exist in the first revision.

---

## 1. Scope

`catalog` is the system's **corporate master data**: the single source of truth about which
articles exist, how they are grouped, and in which units they are measured. It has no branch
dimension (SUP-03, `docs/especificacion_requerimientos.md:301`): one SKU is the same physical
article across the whole network. Every other business module — `inventory`, `pricing`,
`purchases`, `sales`, `transfers`, `analytics` — will eventually read from it.

This change delivers the administrative CRUD for **categories**, **products** and **per-product
units of measure with their conversion factor**, plus the catalog read surface consumed by every
authenticated role. Tables `categories`, `products` and `product_units` already exist
(`backend/init-db/01-init-schema.sql:78-117`); this change **adds two columns and one index to
them** (§1.2) and adds **no new table**.

### 1.1. Out of scope

| Excluded | Reason |
| :--- | :--- |
| Any read or mutation of balances, Kardex, thresholds or costs | Owned by `inventory`. `catalog` **never** mutates stock and **never** writes to the Kardex, so RN-02 does not apply to it. The one exception is a read-only precondition check described in §2.2. |
| Sale prices and price lists | `pricing` (CU-VEN-02). `products` has no price column and by architecture decision will not get one (arch. §4, `docs/decisiones_arquitectura_tecnica.md:246`). |
| The **outbound** `shared` port through which other modules will read the catalog | No consumer exists yet. Declaring a port with neither implementer nor caller is speculation. The constraint governing its future shape is fixed in §2.3 so the first consumer does not have to rediscover it. Note this is the *opposite direction* from the inbound port of §2.2, which this change **does** deliver. |
| **The HTTP exposure of the base-unit change** | Deferred (PA-08). The domain rule (R-08) and its `shared` port (§2.2) **are** delivered by this change; no endpoint reaches them, because with no implementer of the port every call would answer `409` forever, and a permanently-failing operation in the OpenAPI document is worse than an absent one. The endpoint ships with `inventory`. **Within this change's scope `base_unit` is therefore de-facto immutable.** |
| Applying a unit conversion to a real operation (selling in boxes, receiving in pallets) | `catalog` **declares** the factor; converting the quantity when a balance moves belongs to the mutating module (CU-VEN-01 FA-01, `docs/casos_de_uso.md:329`). This contract guarantees the factor exists, is > 0 and is queryable. |
| Images, attachments, barcodes, per-product dynamic attributes | No RF requires them and no column supports them. |
| Category hierarchy (nested categories) | `categories` has no parent column; the model is deliberately flat. |
| Bulk/file import of the catalog | No RF requires it. |
| Physical deletion of products **or categories** | Products: forbidden in practice — `branch_inventories`, `kardex_movements`, `sale_items` and `purchase_order_items` reference `products` with `ON DELETE RESTRICT`. Categories: forbidden by decision (PA-05); both are now logical-disable only. |
| Retroactively recomputing history after a base-unit change | Out of scope by construction: §4/R-07 only permits the change when there is **no** history to recompute. |

### 1.2. Schema changes this contract introduces

This change **does** modify `backend/init-db/01-init-schema.sql`. Three edits, no new table, so
the validator's `igual "20 tablas creadas"` (`scripts/validar_esquema.sh:78`) is unaffected.

| # | Edit | Rationale |
| :--- | :--- | :--- |
| S-1 | `categories`: add `is_active BOOLEAN NOT NULL DEFAULT TRUE` | Logical disable, consistent with `products`, `users` and `branches` (PA-05). |
| S-2 | `categories`: add `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | Without it, the category resource could not honestly report when it last changed. The schema has **no triggers at all** (verified across the whole DDL), so `updated_at` is application-maintained here exactly as it already is for `products` and `users`. |
| S-3 | `product_units`: add `CREATE UNIQUE INDEX uq_product_units_single_default ON product_units(product_id) WHERE is_default_sale_unit` | Makes "at most one default sale unit per product" a schema guarantee (RNF-INT-03), mirroring the existing `uq_price_lists_single_default ON price_lists(is_default) WHERE is_default` (`01-init-schema.sql:145`) that solves the identical problem one module over. |

**Seed data needs no edit — verified, not assumed.**

- `02-seed-data.sql:45` inserts categories with an explicit column list `(external_id, name, description)`, so S-1 and S-2 are filled by their defaults.
- `02-seed-data.sql:58-65` contains exactly one `is_default_sale_unit = TRUE` per product (products 1–5, one each), so S-3 loads cleanly against the existing seed rather than failing it.

**DT-01 friction, stated rather than discovered later.** `backend/init-db/` only runs against an
empty volume (`docs/deuda_tecnica.md:47`). Any environment with an already-initialised volume
needs `docker compose down -v` to pick these edits up. This change **must not** add Flyway
alongside `init-db/` (CLAUDE.md; DT-01 is the migration path and it is a replacement, not a
coexistence). The two new columns and the index make DT-01 marginally larger, and that is the
accepted cost of not carrying the inconsistency.

---

## 2. Affected modules

### 2.1. Dependencies

| Module | Role in this change | Direction |
| :--- | :--- | :--- |
| `catalog` | **New.** All domain, use cases and adapters in this contract. | — |
| `shared` | **Extended.** Reuses `shared/security` (authenticated principal) and `shared/audit` (`AuditWritePort`, `AuditEntryCommand`) as-is, and **gains one new inbound port** for the stock-presence precondition of §2.2. | `catalog` → `shared` |
| `inventory` | **Named, not built.** It is the future implementer of the §2.2 port. This change ships no `inventory` code; §2.2/R-07 defines exactly how `catalog` behaves while that implementation is absent. | `inventory` → `shared` (later) |
| `iam` | **Touched at one point:** the filter chain's route rules live in `iam/infrastructure/config/SecurityConfig.java:59-72`. Adding `/api/catalog/**` matchers there creates **no type dependency** between modules (they are strings), so no ArchUnit rule is violated. | no code dependency |

**No cycle is introduced.** `catalog` imports no business module; the module graph gains no
module-to-module edge and `noHayCiclosEntreModulos`
(`backend/src/test/java/com/optiplant/inventory/ModuleBoundariesTest.java:80-87`) stays green.

### 2.2. The stock-presence precondition, without a cycle

R-07 permits changing `base_unit` only when the product has neither balances nor movements.
`catalog` cannot ask `inventory` directly: `ningunModuloEntraAlInteriorDeOtro`
(`ModuleBoundariesTest.java:67-77`) uses `slices().notDependOnEachOther()` ignoring **only**
`com.optiplant.inventory.shared..`, so no module may import *any* class of another — not even a
public port interface. And a direct `catalog → inventory` import would close a cycle anyway, since
`inventory → catalog` is unavoidable.

**Mandated mechanism.** The precondition **MUST** be expressed as an **inbound port declared in
`shared`**, consumed by `catalog` and implemented by an `inventory` adapter — structurally the
same move as `shared/audit/AuditWritePort`, only in the opposite direction. The resulting graph is
`catalog → shared ← inventory`: two edges into `shared`, zero between modules, no cycle. The port's
signature must stay framework-free and traffic only in `external_id`-shaped UUIDs and primitives,
so that `sharedEsUnaHoja` (`ModuleBoundariesTest.java:90-97`) keeps holding.

**The port is narrow, single-purpose and answers one question** (PA-09): conceptually
`boolean isProductUntouched(UUID productExternalId)` — the exact name is the designer's, matching
`shared/audit`'s style. It **MUST NOT** grow into a general stock-reading API. A broader
"stock summary" surface in `shared` would become a cross-module read API that no module owns, and
would hand `catalog` inventory data it has no business holding (§7.1, point 4). One consumer
(`catalog`), one future implementer (`inventory`), one question.

**The predicate the port answers must be exactly this**, and the Kardex clause is the binding half:

> A product is *untouched* when **(a)** it has no `branch_inventories` row with a non-zero
> `current_stock`, `reserved_stock` or `in_transit_stock`, **and (b)** it has no
> `kardex_movements` row at all, in any branch, ever.

Clause (b) is not redundant. A product whose stock has returned to zero still has history —
quantities, unit costs and running balances — recorded in the *old* base unit. Testing only for
current stock would let a base-unit change silently reinterpret that history, which is precisely
what RN-13 exists to prevent.

**Two alternatives are explicitly rejected, so the designer does not re-litigate them:**

1. *A coordinating application service outside both modules.* It has no legal home: CLAUDE.md
   forbids any new class in a direct subpackage of `com.optiplant.inventory` that is not a
   business module, which is why `SecurityConfig` had to move into `iam`.
2. *A `catalog` persistence adapter querying `branch_inventories` / `kardex_movements` directly.*
   This would pass every ArchUnit rule — SQL strings import no types — while making `catalog` a
   silent co-owner of another module's tables. It **MUST NOT** be done. ArchUnit's blindness to it
   is the reason this contract names it rather than trusting the build to catch it.

**Behaviour while `inventory` does not exist — fail closed.** No implementation of the port ships in
this change. The domain rule **MUST** treat an absent implementation as *"cannot prove the product
is untouched"* and refuse the change. It **MUST NOT** fail open.

**Consequence, decided rather than discovered (PA-08): no HTTP endpoint mutates `base_unit` in this
change.** Since every call would fail closed until `inventory` exists, publishing the operation
would put a permanently-`409` route into the OpenAPI document — worse than an absent one, because
clients would code against an operation that has never once succeeded. So:

- **Delivered here:** the `shared` port interface, the domain rule R-08 and the model that enforces
  it, unit-tested against a stubbed port.
- **Deferred to the change that brings `inventory`:** the HTTP exposure
  (`PATCH /products/{externalId}/base-unit`), together with the port implementation that finally
  makes it answerable.
- **Therefore, within this change's scope `base_unit` is de-facto immutable**: it is set at creation
  (R-06) and no endpoint in §6 can alter it. `PUT /products/{externalId}` does not accept the field
  at all (§6.2).

**Atomicity, contracted now for the slice that ships it.** When the endpoint arrives, the
precondition check and the `base_unit` update **MUST** run inside the same transaction (§8), so a
concurrent goods receipt cannot create the first movement between the check and the commit. Fixing
this now is the point of contracting the rule ahead of its exposure: `inventory` implements against
something already settled.

### 2.3. Constraint on the first future consumer

When `inventory` (or anyone else) needs to resolve a product by `external_id` or read its
conversion factor, that **outbound** contract **MUST** likewise be declared as a port in `shared`
with framework-free types and implemented by a `catalog` adapter. Any other route fails the build.
It is out of scope here only because no consumer exists yet (§1.1).

### 2.4. Decision the designer inherits

Authorization for `catalog`'s routes has two possible homes and neither is obvious:

1. Extend the single filter chain in `iam/infrastructure/config/SecurityConfig.java` with
   HTTP-method-scoped matchers (the project's current state; no type dependency).
2. Enable method security (`@EnableMethodSecurity`, currently **not** active — verified: zero
   occurrences of `@EnableMethodSecurity` or `@PreAuthorize` under `backend/src/main`) and annotate
   `catalog`'s controllers.

This contract does not choose: it fixes **what** must end up authorized (§5) and leaves the
**where** to the design.

---

## 3. Traceability

### 3.1. RF → CU → HU chain this change materializes

| RF | Text (source) | CU | HU |
| :--- | :--- | :--- | :--- |
| **RF-INV-01** — Product Catalog | CRUD of products with SKU, name, description, category and unit(s) of measure (`especificacion_requerimientos.md:94`) | CU-INV-01 — Manage product and category catalog (`casos_de_uso.md:111`) | HU-INV-06 (`historias_de_usuario.md:184-196`) |
| **RF-INV-02** — Multiple Units of Measure | Associate and convert multiple units per product (`especificacion_requerimientos.md:95`) | CU-INV-02 — Manage units of measure and conversion factors (`casos_de_uso.md:112`) | HU-INV-06 (`historias_de_usuario.md:184-196`) |

**HU-INV-06** is the **only** backlog story tracing to RF-INV-01/RF-INV-02 and to
CU-INV-01/CU-INV-02 (`historias_de_usuario.md:190`, verified by exhaustive search of the document).
Its four acceptance criteria become numbered requirements in §4.

> **Priority asymmetry, recorded and deliberately not resolved here:** RF-INV-01 and RF-INV-02 are
> `Must` (`especificacion_requerimientos.md:174`), while HU-INV-06 — their only story — is `Should`
> (`historias_de_usuario.md:190`). No `docs/` file is edited to reconcile this: it is not this
> contract's work, and changing the traceability matrix would mean redoing the prioritization.

**Neither CU-INV-01 nor CU-INV-02 has an extended specification** (only their catalog rows,
`casos_de_uso.md:111-112`). §4 therefore derives the flow from the RF text, HU-INV-06's criteria
and the real schema constraints — the same procedure, declared the same way, that
`openspec/specs/user-administration/spec.md:5` used for CU-SEG-02.

### 3.2. Rules and NFRs that constrain the module without being materialized by it

| ID | How it constrains `catalog` |
| :--- | :--- |
| **RN-13** (`especificacion_requerimientos.md:198`) | Balances always operate in the base unit. It is the whole reason `base_unit` may only change on a product with no history (R-07, §2.2). |
| **RN-14** (`:199`) | The branch derives from the session. `catalog` is corporate: no endpoint accepts a branch identifier in path, query or body. |
| **RN-12** (`:197`) | Kardex, audit and sales rows are never physically deleted. Reinforces logical-only disable for products and categories. |
| **RNF-SEC-01/03** (`:235,237`) | Strict RBAC; master-data management is `ADMIN`-only (`casos_de_uso.md:74`). |
| **RNF-SEC-05** (`:239`) | Backend validation, parameterized statements, `external_id` exposure only. |
| **RNF-API-02** (`:275`) | Consistent REST semantics, uniform errors, only `external_id` in routes and payloads. |
| **RNF-PER-01/04** (`:224,227`) | p95 < 200 ms on reads and mandatory pagination with a page cap. |
| **RNF-INT-01** (`:230`) | Every mutation atomic. |
| **RNF-INT-03** (`:232`) | Critical invariants live in the schema too. SKU uniqueness already did (`01-init-schema.sql:92`); S-3 adds the default-sale-unit invariant. |
| **RNF-MAN-01/02** (`:255,256`) | Domain testable without infrastructure; boundaries verified by ArchUnit. |
| **RF-VAL-02** (`:168`) | Immutable log of sensitive events. Justifies auditing master-data mutations (R-14). This is **not** a claim that `catalog` *materializes* RF-VAL-02: the matrix assigns it to CU-SEG-04 and CU-INV-05 (`casos_de_uso.md:601`). |

### 3.3. Effect on `scripts/validar_trazabilidad.py`

This change **creates no new identifier** and **modifies no file under `docs/`**. The validator only
walks `docs/` (`scripts/validar_trazabilidad.py:21`), so every ID cited here already exists in the
SRS and validation stays green without touching the traceability matrix. **No task for the human
in this area.** It still runs in §11 as a regression guard.

---

## 4. Behavioural contract

RFC 2119 wording.

### Categories

**R-01 — Only `ADMIN` administers the master; every authenticated user reads it.**
The system MUST restrict creation, editing, disabling and enabling of categories, products and
units to the `ADMIN` role (`casos_de_uso.md:74`). It MUST allow any authenticated user to read the
catalog, regardless of role or branch (`casos_de_uso.md:55`, `:75`).

- **Given** an authenticated `OPERATOR` or `BRANCH_MANAGER`, **when** they invoke any catalog
  mutation endpoint, **then** the system responds `403 Forbidden` and persists nothing.
- **Given** an authenticated `OPERATOR` from any branch, **when** they list or read products,
  categories or units, **then** the system responds `200 OK` with exactly what an `ADMIN` would see.
- **Given** a caller with no token or an expired token, **when** they invoke any endpoint of this
  module, **then** the system responds `401 Unauthorized`.

**R-02 — Category name is unique and normalized.**
The system MUST reject creating or editing a category whose name, after trimming surrounding
whitespace, matches another category's name case-insensitively. It MUST persist the trimmed name.
It MUST NOT accept an empty name or one longer than 100 characters
(`categories.name VARCHAR(100) NOT NULL UNIQUE`, `01-init-schema.sql:81`).

- **Given** a category "Fertilizantes", **when** an `ADMIN` creates "  fertilizantes  ", **then** the
  system responds `409 Conflict` with code `duplicate_category_name`.
- **Given** a 101-character name, **when** it is submitted, **then** the system responds
  `400 Bad Request` without reaching the domain (RNF-SEC-05).

**R-03 — Categories have a logical lifecycle.**
Following S-1/S-2, the system MUST support disabling and re-enabling a category, MUST expose its
`active` state and its `updatedAt`, and MUST NOT physically delete a category under any
circumstance. `updated_at` MUST be advanced by the application on every edit, disable and enable:
the schema has no trigger to do it.

- **Given** an active category, **when** an `ADMIN` disables it, **then** `is_active` becomes `FALSE`,
  `updated_at` advances, and the row is preserved.
- **Given** an already-disabled category, **when** an `ADMIN` disables it again, **then** the system
  responds successfully and the state is unchanged (idempotent).

**R-04 — A category with active products cannot be disabled.**
The system MUST reject disabling a category that still has at least one **active** product, and MUST
allow it when the category has no products or only inactive ones. Leaving active products hanging
off a disabled category would make the master internally inconsistent; inactive products are already
out of circulation and do not block.

- **Given** a category with one active product, **when** an `ADMIN` disables it, **then** the system
  responds `409 Conflict` with code `category_in_use` and changes nothing.
- **Given** a category whose only two products are inactive, **when** an `ADMIN` disables it, **then**
  the operation succeeds.
- **Given** a non-existent `external_id`, **when** an `ADMIN` disables it, **then** the system
  responds `404 Not Found`.

**R-05 — A product cannot be attached to an inactive category.**
The system MUST reject creating a product in an inactive category, and MUST reject editing a product
to move it into one.

- **Given** an inactive category, **when** an `ADMIN` creates a product referencing it, **then** the
  system responds `409 Conflict` with code `category_inactive` and persists nothing.

### Products

**R-06 — Product creation with a unique, normalized SKU.**
The system MUST create a product with SKU, name, an existing active category and a base unit. It
MUST normalize the SKU to trimmed uppercase before persisting, so that the existing `UNIQUE` index
(`01-init-schema.sql:92`) is a sufficient guarantee of SUP-03 and `abc-1` cannot coexist with
`ABC-1` as two different articles. It MUST reject a duplicate SKU and a non-existent category. The
product is born **active**.

- **Given** a unique SKU, an existing active category and a valid base unit, **when** an `ADMIN`
  creates the product, **then** it is persisted with `is_active = TRUE` and the system responds
  `201 Created`.
- **Given** SKU `FERT-NPK-151515` already registered, **when** an `ADMIN` creates `fert-npk-151515`,
  **then** the system responds `409 Conflict` with code `duplicate_sku`.
- **Given** a non-existent category `external_id`, **when** the product is created, **then** the
  system responds `404 Not Found` with code `category_not_found` and persists nothing.
- **Given** two concurrent creations of the same SKU, **when** both commit, **then** exactly one
  succeeds and the other receives `409 Conflict`; the guarantee is the schema's `UNIQUE`
  constraint, not the in-memory pre-check.

**R-07 — Base unit format.**
The system MUST accept as `base_unit` an uppercase-normalized string matching `^[A-Z0-9_]{1,20}$`
and MUST NOT impose a closed vocabulary. The seed already contains `KG`, `LITRO`, `BOLSA_80K_SEM`
and `ROLLO` (`02-seed-data.sql:52-56`); two of those are article-specific, so a closed enumeration
would break the initial load.

- **Given** `baseUnit = "kg"`, **when** the product is created, **then** `KG` is persisted.
- **Given** `baseUnit = "Saco de 50"`, **when** the product is created, **then** the system responds
  `400 Bad Request`: whitespace is not in the format.

**R-08 — Base unit may change only on a product with no balances and no movements.**
*(Domain rule and `shared` port delivered by this change; HTTP exposure deferred — §2.2, PA-08. The
scenarios below are therefore stated over the domain operation, not over a route, and are verified
by unit tests against a stubbed port.)*

The system MUST allow the base-unit change **only** when the stock-presence port of §2.2 confirms
the product is *untouched* as defined there. It MUST refuse the change otherwise, and MUST refuse it
when the port has no implementation available.

*Declared tension:* RF-INV-01 lists the unit of measure among the CRUD fields with no
qualification, so this requirement **narrows its literal reading** — deliberately, and at the user's
explicit direction (PA-02). RN-13 is what forces the precondition: every balance, Kardex entry and
historical cost is expressed in the base unit in force when it was written, and changing that unit
under existing history would silently reinterpret all of it.

- **Given** a product with no `branch_inventories` balance and no `kardex_movements` row, **when** the
  base unit changes from `KG` to `LITRO`, **then** the change is applied and `updated_at` advances.
- **Given** a product with non-zero stock in one branch, **when** the change is attempted, **then**
  the domain refuses it and no field of the product is modified.
- **Given** a product whose stock is zero everywhere but which has at least one Kardex movement in
  its history, **when** the change is attempted, **then** the domain refuses it: past movements are
  recorded in the old unit.
- **Given** no available implementation of the stock-presence port — **the state of this change** —
  **when** the change is attempted, **then** the domain refuses it and never applies it.
- **Given** the API surface of §6, **when** any client attempts to alter a product's base unit
  through it, **then** there is no operation to do so: the field is fixed at creation and `PUT`
  rejects it (§6.2).

**R-09 — Product editing.**
The system MUST allow an `ADMIN` to edit a product's name, description, category and SKU, applying
the same SKU and category validations as creation, and MUST NOT allow `external_id` to be modified.
A `base_unit` edit is governed by R-08.

- **Given** an existing product, **when** an `ADMIN` moves it to another active category, **then** the
  change is persisted and `updated_at` advances.
- **Given** a SKU belonging to another product, **when** an `ADMIN` tries to assign it, **then** the
  system responds `409 Conflict` with code `duplicate_sku`.

**R-10 — Product disable is logical and does not touch stock.**
The system MUST disable a product by setting `is_active = FALSE`, MUST NOT delete or alter its
inventory rows, Kardex entries or historical sales, and MUST keep returning it on lookup by
`external_id`. An inactive product MUST stay readable so history remains legible; preventing its
selection in new sales, purchases or transfers is the mutating modules' obligation (CU-VEN-01
EX-03, `casos_de_uso.md:336`), for which `catalog` supplies the state.

- **Given** an active product with stock in two branches, **when** an `ADMIN` disables it, **then**
  `is_active` becomes `FALSE` and both branches' balances are untouched.
- **Given** an already-inactive product, **when** an `ADMIN` disables it again, **then** the system
  responds successfully and the state is unchanged (idempotent).
- **Given** an inactive product, **when** anyone reads it by `external_id`, **then** it is returned
  with `active: false`, not `404`.

**R-11 — Re-enabling.**
The system MUST allow an `ADMIN` to re-enable a disabled product, and likewise a disabled category.
*(An extension beyond RF-INV-01's literal text, which names only "disable"; without it an
accidental disable would be irreversible through the API. Confirmed by the user as PA-03.)*

- **Given** an inactive product, **when** an `ADMIN` re-enables it, **then** `is_active` becomes
  `TRUE` and it reappears in the default listing.
- **Given** an inactive product whose category is also inactive, **when** an `ADMIN` re-enables the
  product, **then** the system responds `409 Conflict` with code `category_inactive`: re-enabling
  must not recreate the inconsistency R-04 and R-05 exist to prevent.

**R-12 — Search and paginated listing, active-only by default.**
The system MUST expose the product listing paginated, filterable by free text against SKU or name
(case-insensitive), by category (`external_id`) and by active state, covering step 1 of CU-INV-04
(`casos_de_uso.md:516`). **By default it MUST return only active products**; inactive ones are
returned only when the caller asks explicitly (PA-07). It MUST enforce a maximum page size
(RNF-PER-04) and MUST NOT accept a sort field outside a closed allow-list. The same default applies
to the category listing.

- **Given** no `active` parameter, **when** a caller lists products, **then** only active products
  are returned.
- **Given** `active=false`, **when** a caller lists products, **then** only inactive products are
  returned; **given** `active=all`, **then** both are returned.
- **Given** `active=maybe`, **when** a caller lists products, **then** the system responds
  `400 Bad Request`.
- **Given** `size=5000`, **when** a caller lists, **then** at most the page cap is returned, never an
  unbounded response.
- **Given** `sort=(select 1)`, **when** a caller lists, **then** the system responds `400 Bad Request`
  and never interpolates the value into the query.
- **Given** the term `npk`, **when** a caller searches, **then** the product with SKU
  `FERT-NPK-151515` appears in the result.

### Units of measure per product

**R-13 — Alternative unit with a positive factor.**
The system MUST allow associating alternative units to a product with their conversion factor
expressed in base units, MUST reject a factor less than or equal to zero (HU-INV-06 acceptance
criterion, `historias_de_usuario.md:195`, and `CHECK (conversion_factor > 0)`,
`01-init-schema.sql:110`), and MUST reject a `unit_name` repeated within the same product
(`uq_product_unit`, `:113`). `unit_name` takes the same normalization and format as the base unit
(R-07), with a maximum length of 50.

- **Given** a product whose base unit is `UNIDAD`, **when** an `ADMIN` defines `CAJA` with factor 12,
  **then** the unit is registered and the product can be operated in both
  (`historias_de_usuario.md:193`).
- **Given** a factor of `0` or `-1`, **when** saving is attempted, **then** the system responds
  `400 Bad Request` with code `invalid_conversion_factor`.
- **Given** a product that already has `CAJA`, **when** an `ADMIN` adds another `CAJA`, **then** the
  system responds `409 Conflict` with code `duplicate_product_unit`.
- **Given** a unit whose `unitName` equals the product's base unit, **when** it is saved with a
  factor other than 1, **then** the system rejects it: the base unit is worth exactly 1 base unit,
  and accepting otherwise would allow two contradictory conversions of the same name.
- **Given** a unit of a product, **when** an `ADMIN` deletes it, **then** it disappears without
  affecting any balance or movement: no other table in the schema references `product_units`
  (verified across the full DDL), because every persisted quantity is already in base units (RN-13).

**R-14 — At most one default sale unit, enforced by the schema.**
The system MUST guarantee that a product has at most one `product_units` row with
`is_default_sale_unit = TRUE`. When marking a new default it MUST clear the previous one **within
the same transaction**. After S-3 this invariant is enforced by the database as well as the domain,
so a defect in the application cannot produce two defaults.

- **Given** a product whose `SACO_50KG` unit is the default, **when** an `ADMIN` marks
  `BULTITO_10KG` as default, **then** exactly one unit of the product carries the mark when the
  operation ends.
- **Given** a direct SQL insert of a second default unit for the same product, **when** it executes,
  **then** the database rejects it (`uq_product_units_single_default`).
- **Given** two different products, **when** each marks one default unit, **then** both are accepted:
  the index is scoped by `product_id`.
- **Given** a product with no default unit at all, **when** it is read, **then** it is returned
  without one and does not fail: the mark is optional (`DEFAULT FALSE`, `01-init-schema.sql:111`).

### Cross-cutting

**R-15 — Every master-data mutation leaves an audit trail.**
The system MUST record every catalog creation, edit, disable and enable in the immutable audit log
through the existing synchronous port `shared/audit/AuditWritePort`, **inside the same transaction**
as the operation (PA-06). The catalog is corporate, so the entry is written with `branchId = null`,
a value `AuditEntryCommand` already admits explicitly. This supports RF-VAL-02 and makes the
mutation queryable from CU-SEG-04.

- **Given** an `ADMIN` disabling a product, **when** the operation commits, **then** an audit entry
  exists with actor, entity, affected `external_id` and the before/after payloads.
- **Given** a failure writing the audit entry, **when** it occurs, **then** the catalog mutation is
  fully rolled back: a master-data mutation without a trail is not acceptable.

**R-16 — The catalog has no branch dimension.**
The system MUST NOT accept any branch identifier in any route, query parameter or body of this
module, and MUST NOT vary its response by the caller's branch (SUP-03, RN-14).

- **Given** two users from different branches, **when** both read the same product, **then** they
  receive exactly the same representation.

---

## 5. Authorization matrix

Checked against the "Gestionar catálogo maestro y unidades de medida" row of the RBAC matrix
(`docs/casos_de_uso.md:74`: `ADMIN` ✅, `BRANCH_MANAGER` ❌, `OPERATOR` ❌) and against "Consulta el
catálogo" among the operator's responsibilities (`:55`).

| Operation | `ADMIN` | `BRANCH_MANAGER` | `OPERATOR` | Scoping rule |
| :--- | :---: | :---: | :---: | :--- |
| List / read categories | ✅ | ✅ | ✅ | Global. No branch filter. Active-only by default. |
| Create / edit category | ✅ | ❌ | ❌ | Corporate. `403` for other roles. |
| Disable / enable category | ✅ | ❌ | ❌ | Corporate. |
| List / search / read products | ✅ | ✅ | ✅ | Global. Active-only by default; inactive on explicit request. |
| Create / edit product | ✅ | ❌ | ❌ | Corporate. Base unit fixed at creation (PA-08). |
| Disable / enable product | ✅ | ❌ | ❌ | Corporate. |
| List units of a product | ✅ | ✅ | ✅ | Global. |
| Create / edit / delete unit | ✅ | ❌ | ❌ | Corporate. |

Application notes:

1. Authorities are exactly `ADMIN`, `BRANCH_MANAGER`, `OPERATOR`, **with no `ROLE_` prefix**. With
   Spring Security use `hasAuthority()` / `hasAnyAuthority()`, never `hasRole()`
   (`decisiones_arquitectura_tecnica.md:149`; `SecurityConfig.java:69-71` already does this).
2. A corporate `ADMIN` may have `branch_id = NULL` (`01-init-schema.sql:33`). No authorization rule
   in this module may require a branch on the principal: doing so would lock the corporate
   administrator out of administering the master data.
3. An unauthorized caller MUST receive `403` before the use case executes, so the response cannot
   reveal whether the resource exists.

---

## 6. API surface

Conventions inherited from `iam` and verified in code: `/api` prefix, `external_id` (UUID) in routes
(`UserAdminController.java:69`), `page` (0-based) / `size` pagination with a cap
(`BranchAdminController.java:37-38,73-75`), page envelope `{ content, totalElements, page, size }`
(`:99`), and `{ code, message }` errors (`IamExceptionHandler.java:102`).

**Route choice.** `catalog` uses one `/api/catalog/**` tree instead of `iam`'s `/api/admin/*`
pattern, because — unlike users and branches — its **read** surface is open to all three roles and
only its **mutation** surface is `ADMIN`-only. Splitting the same resource into two trees by
audience would duplicate every path; the correct split is by HTTP method.

**Verb choice for logical disable.** Disable is exposed as `PATCH /{externalId}/disable` and
`PATCH /{externalId}/enable`, **not** as `DELETE`. This is a deliberate reading of the PA-05
instruction, which fixed the *semantics* (logical disable, no physical deletion) rather than the
verb: a `DELETE` that does not delete misrepresents the contract to every client and to the
OpenAPI document, and `iam` already established `PATCH /{externalId}/disable`
(`UserAdminController.java:77`, `BranchAdminController.java:64`) for exactly this. Consequently
there is **no** `DELETE` endpoint for categories or products anywhere in this surface.

Every exposed identifier is an `external_id` (UUID). **No numeric `id` appears in any route,
parameter or payload** (RNF-API-02, RNF-SEC-05).

### 6.1. Categories

| Method | Path | Role | Success |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/catalog/categories` | authenticated | `200` |
| `GET` | `/api/catalog/categories/{externalId}` | authenticated | `200` |
| `POST` | `/api/catalog/categories` | `ADMIN` | `201` + `Location` |
| `PUT` | `/api/catalog/categories/{externalId}` | `ADMIN` | `200` |
| `PATCH` | `/api/catalog/categories/{externalId}/disable` | `ADMIN` | `200` |
| `PATCH` | `/api/catalog/categories/{externalId}/enable` | `ADMIN` | `200` |

- **List query:** `name` (contains, optional), `active` (`true` \| `false` \| `all`, default `true`),
  `page` (integer ≥ 0, default `0`), `size` (integer, default `20`, cap `100`). Fixed ascending sort
  by `name`.
- **Request (`POST` / `PUT`):** `{ "name": string(1..100), "description": string|null }`.
- **Resource:**
  `{ "externalId": uuid, "name": string, "description": string|null, "active": boolean, "activeProductCount": integer, "createdAt": timestamp, "updatedAt": timestamp }`.
  `active` and `updatedAt` exist because of S-1/S-2. `activeProductCount` is what lets a client
  anticipate R-04's `409` — it counts **active** products specifically, since only those block the
  disable.
- **Page:** `{ "content": [Category], "totalElements": long, "page": int, "size": int }`.

### 6.2. Products

| Method | Path | Role | Success |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/catalog/products` | authenticated | `200` |
| `GET` | `/api/catalog/products/{externalId}` | authenticated | `200` |
| `POST` | `/api/catalog/products` | `ADMIN` | `201` + `Location` |
| `PUT` | `/api/catalog/products/{externalId}` | `ADMIN` | `200` |
| `PATCH` | `/api/catalog/products/{externalId}/disable` | `ADMIN` | `200` |
| `PATCH` | `/api/catalog/products/{externalId}/enable` | `ADMIN` | `200` |

- **List query:** `q` (free text against SKU or name, case-insensitive, optional), `categoryId`
  (UUID, optional), `active` (`true` \| `false` \| `all`, default `true`), `page`, `size` (default
  `20`, cap `100`), `sort` (allow-list `sku` \| `name` \| `createdAt`, default `sku`), `direction`
  (`asc` \| `desc`, default `asc`).
- **Request (`POST`):**
  ```json
  {
    "sku": "FERT-NPK-151515",
    "name": "Fertilizante Triple 15",
    "description": "text or null",
    "categoryExternalId": "uuid",
    "baseUnit": "KG",
    "units": [
      { "unitName": "SACO_50KG", "conversionFactor": 50.0, "defaultSaleUnit": true }
    ]
  }
  ```
  `units` is optional; when present each element is validated by R-13/R-14 and persisted in the same
  transaction as the product.
- **Request (`PUT`):** `{ "sku", "name", "description", "categoryExternalId" }`. It **rejects**
  `baseUnit` with `400 invalid_request` if present: the base unit is fixed at creation in this
  change (§2.2, PA-08), and silently dropping the field would let a caller believe a change took
  effect that never did. It does not accept `units` either: those have their own subresource.
  When the deferred base-unit operation ships, it arrives as its own endpoint — not folded in here,
  because it carries a precondition and a distinct failure mode, and burying it in the general edit
  would make an ordinary rename fail for reasons the caller never asked about.
- **Resource (detail):**
  ```json
  {
    "externalId": "uuid",
    "sku": "FERT-NPK-151515",
    "name": "Fertilizante Triple 15",
    "description": "text or null",
    "baseUnit": "KG",
    "active": true,
    "category": { "externalId": "uuid", "name": "Fertilizantes", "active": true },
    "units": [
      { "externalId": "uuid", "unitName": "SACO_50KG", "conversionFactor": 50.0, "defaultSaleUnit": true }
    ],
    "createdAt": "timestamp",
    "updatedAt": "timestamp"
  }
  ```
- **Resource (list item):** the same object **without** `units` and `description`, so a 100-product
  page does not trigger a per-row query.
- **Field limits, derived from the schema:** `sku` ≤ 50, `name` ≤ 150, `baseUnit` ≤ 20 matching
  `^[A-Z0-9_]{1,20}$`, `description` free (`TEXT`).

### 6.3. Units of measure of a product

| Method | Path | Role | Success |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/catalog/products/{productExternalId}/units` | authenticated | `200` |
| `POST` | `/api/catalog/products/{productExternalId}/units` | `ADMIN` | `201` + `Location` |
| `PUT` | `/api/catalog/products/{productExternalId}/units/{unitExternalId}` | `ADMIN` | `200` |
| `DELETE` | `/api/catalog/products/{productExternalId}/units/{unitExternalId}` | `ADMIN` | `204` |

- **Request (`POST` / `PUT`):**
  `{ "unitName": string(1..50, ^[A-Z0-9_]+$), "conversionFactor": decimal(12,4) > 0, "defaultSaleUnit": boolean }`.
- **Resource:**
  `{ "externalId": uuid, "unitName": string, "conversionFactor": decimal, "defaultSaleUnit": boolean, "createdAt": timestamp }`.
  No `updatedAt`: the table has no such column and none is added.
- `DELETE` **is** correct here and is the one physical deletion in the module: a `product_units` row
  is referenced by no other table and holds no history (R-13).
- The unit collection of a product is **not paginated**: it is bounded by the product itself and by
  `uq_product_unit`. This is the justified exception to RNF-PER-04, which requires pagination on
  "potentially unbounded" collections (`especificacion_requerimientos.md:227`).
- The unit MUST belong to the product in the path; if `unitExternalId` exists but hangs off another
  product, the response is `404`, never `200`.

---

## 7. Error taxonomy

Uniform envelope `{ "code": string, "message": string }` with stable `snake_case` codes, as in `iam`
(`IamExceptionHandler.java:32-103`).

| Code | HTTP | When |
| :--- | :---: | :--- |
| `invalid_request` | 400 | Missing required field, length exceeded, invalid unit format, `sort`/`direction`/`active` outside the allow-list, malformed UUID, or `baseUnit` sent to `PUT /products/{externalId}` (§6.2). |
| `invalid_conversion_factor` | 400 | `conversionFactor` ≤ 0, or a unit homonymous with the base unit carrying a factor ≠ 1 (R-13). |
| `category_not_found` | 404 | The category referenced by path or by `categoryExternalId` does not exist. |
| `product_not_found` | 404 | The product in the path does not exist. |
| `product_unit_not_found` | 404 | The unit does not exist, or exists but belongs to another product. |
| `duplicate_category_name` | 409 | Category name already in use (R-02). |
| `duplicate_sku` | 409 | SKU already used by another product (R-06, R-09). |
| `duplicate_product_unit` | 409 | `unit_name` repeated within the same product (`uq_product_unit`). |
| `category_in_use` | 409 | Disabling a category that still has active products (R-04). |
| `category_inactive` | 409 | Creating a product in, moving one into, or re-enabling one under, an inactive category (R-05, R-11). |

Ten codes. **No base-unit error code is defined here**, because PA-08 defers the operation that
would raise one: a code with no reachable path is dead contract. When the endpoint ships with
`inventory`, it needs **two** distinct codes — one for *"the product has history, so no"* and one for
*"the port cannot answer, so I cannot tell"*. Collapsing those two into one would make an
infrastructure gap look like a business rejection to both the caller and whoever reads the logs.
That is a note for the future slice, not a definition in this one.

Codes produced by the security chain, not by this module: `401` with no or invalid token; `403` for
insufficient role.

### 7.1. What the response must not leak

1. **No internal numeric `id`**, in any field, message or `Location` header (RNF-API-02).
2. **No stack trace, SQL statement or database constraint name** in `message`. A
   `DataIntegrityViolationException` — including one raised by the new
   `uq_product_units_single_default` — is translated to the matching conflict code with a
   hand-written message, as `IamExceptionHandler.java:76-80` already does.
3. **No existence revealed to an unauthorized caller**: the role-based `403` resolves before the
   resource is looked up, so an `OPERATOR` cannot use the difference between `403` and `404` to
   probe which `external_id`s exist.
4. **No inventory detail may ever escape through the base-unit path.** The narrow port of §2.2
   returns a boolean precisely so there is nothing to leak. When the deferred endpoint ships, its
   error MUST say only that the product has history — never which branches hold stock, how much, or
   how many movements exist. That is `inventory`'s data, reachable through its own authorized
   endpoints.
5. Cross-branch leakage does not apply: the catalog is corporate and MUST return the same content to
   everyone (R-16). Any per-branch variation would be a defect, not a protection.

---

## 8. Transactional and consistency guarantees

| Guarantee | Content |
| :--- | :--- |
| **Atomicity per operation** | Every mutating use case runs in **one transaction** (RNF-INT-01). Creating a product with inline `units` persists product and units together or persists nothing. |
| **Base-unit precondition and update share a transaction** | Contracted now, exercised when the deferred endpoint ships (PA-08): the stock-presence port call (§2.2) and the `base_unit` write must be in the same transaction, so a concurrent goods receipt cannot slip between check and commit (R-08). Nothing in *this* change mutates `base_unit`, so no such transaction exists yet. |
| **Audit inside the transaction** | The audit write uses the **synchronous output port** `shared/audit/AuditWritePort`, not an event (CLAUDE.md: atomic effects go through a synchronous port). If the audit write fails, the mutation is rolled back (R-15). |
| **Zero domain events** | This change publishes **no** `AFTER_COMMIT` events: no alerting or analytics consumer is interested in master data yet. Introducing one with no recipient would be coupling without purpose. |
| **No pessimistic locking** | `catalog` mutates no balances; there is no hot-row contention and no read-modify-write over stock. The pessimistic locking of arch. §3.3 belongs to `inventory`, `sales` and `transfers`. The two real races — concurrent creation of the same SKU, and concurrent marking of two default sale units — are both resolved by schema constraints (`products.sku UNIQUE`, `uq_product_units_single_default`), not by locks. |
| **Default sale unit** | Clearing the previous default and setting the new one happen in one transaction (R-14); after S-3 the database refuses to store an intermediate state with two defaults, so a partially-applied change cannot be observed or persisted. |
| **Idempotency** | `PUT` and every `PATCH` are idempotent: repeating the request leaves the same state and returns the same response. `DELETE` on an already-deleted unit returns `404`. `POST` is not idempotent and needs no idempotency key: the uniqueness `409` already guards against duplicate creation. |
| **Schema changes are additive** | S-1 and S-2 add columns with defaults; S-3 adds an index. Nothing is dropped or renamed, so the existing seed and every already-written row remain valid (verified in §1.2). |
| **`catalog` never writes the Kardex** | It mutates no balances, so RN-02 does not apply. Any design in which `catalog` touches `kardex_movements` or `branch_inventories` — including the "just query the table" shortcut ruled out in §2.2 — contradicts this contract. |

---

## 9. Non-functional obligations

| Obligation | Measurable target | Source |
| :--- | :--- | :--- |
| Read latency | p95 < 200 ms on listing and detail with 10 000 products in the catalog. | RNF-PER-01 + volumetry `:217` |
| Bounded pagination | Every listing paginated; `size` default 20, cap 100 (same as `iam`); a larger `size` is clamped, not rejected. | RNF-PER-04 |
| Existing indexes used | SKU search uses `idx_products_sku`, category filter uses `idx_products_category`, UUID lookup uses `idx_products_external_id` (`01-init-schema.sql:101-103`). The only new index is S-3, which serves an invariant rather than a query. | RNF-PER-01 |
| Backend validation | Every client input validated before reaching the domain; queries exclusively parameterized. | RNF-SEC-05 |
| No internal identifiers | No primary-key `BIGINT` crosses the HTTP boundary. | RNF-API-02, RNF-SEC-05 |
| Audit | Every master-data mutation leaves an entry with actor, entity, action and payloads. Retention ≥ 5 years (a property of the table, not of this module). | RF-VAL-02, RNF-SEC-08 |
| Schema-level invariants | Default sale unit uniqueness enforced by the database, not only by the domain. | RNF-INT-03 |
| Observability | Logs carry correlation id, user and operation; never credentials. | RNF-OBS-01 |
| Published contract | The §6 endpoints appear in `/v3/api-docs` with their response codes and error structure. | RNF-API-01 |
| Verified boundaries | All five `ModuleBoundariesTest` rules stay green with `catalog` present; `catalog`'s domain imports neither Spring nor JPA. | RNF-MAN-02 |
| Domain coverage | RNF-MAN-01 sets 80% on the domain layer, but **no coverage tool is configured** (`openspec/config.yaml`: `coverage.available: false`). The verifiable substitute is the named test list in §11; announcing a percentage nobody measures would be an unexecuted claim. | RNF-MAN-01 |

---

## 10. Rollback

The schema edits are what make this worth stating. S-1, S-2 and S-3 are additive: reverting the
change means dropping the index and the two columns, and no already-written row becomes invalid in
the meantime. Because `init-db/` only runs against an empty volume, rollback in a development
environment is `git revert` plus `docker compose down -v`; there is no deployed environment with
persistent data to migrate back. The application code is a new module with no caller, so removing
`catalog` leaves the rest of the system exactly as it is today.

---

## 11. Definition of done

Every item is a command someone runs or a file someone opens.

**Automated verification**

- [ ] `cd backend && ./mvnw verify` green — includes ArchUnit (`ModuleBoundariesTest`) and the
      Testcontainers integration tests.
- [ ] `./scripts/validar_esquema.sh` green — **mandatory now**, since `backend/init-db/` changes
      (S-1, S-2, S-3).
- [ ] `python3 scripts/validar_trazabilidad.py` green.
- [ ] `git diff --stat docs/` **empty**: this change creates no identifier and does not touch the
      traceability matrix (§3.3).

**New invariants in `scripts/validar_esquema.sh`** (written with the existing
`rechaza` / `acepta` / `igual` helpers, in a new catalog section)

- [ ] `rechaza` — a product cannot have two default sale units:
      `UPDATE product_units SET is_default_sale_unit = TRUE WHERE product_id = 1 AND unit_name = 'BULTITO_10KG'`
      (product 1 already has `SACO_50KG` as default, `02-seed-data.sql:59-60`).
- [ ] `acepta` — two different products may each have their own default unit (guards against an
      index written without `product_id`, which would let one product's default block every other).
- [ ] `igual` — `SELECT count(*) FROM categories WHERE is_active IS NULL` returns `0`.
- [ ] `igual` — the seeded categories default to active:
      `SELECT count(*) FROM categories WHERE is_active` returns `4`.
- [ ] The existing `igual "20 tablas creadas"` check still passes unchanged: no table is added.

**Domain unit tests** (`*Test`, no Docker)

- [ ] SKU normalization: `abc-1` and `ABC-1` collide (R-06).
- [ ] Base-unit and `unit_name` format and normalization, including whitespace rejection (R-07).
- [ ] Base-unit rule accepted when the product is untouched, refused when it has balances, refused
      when it has only Kardex history, refused when the port is unavailable (R-08). The port is
      stubbed, so all four cases are unit-testable without Docker — and these tests are what keep
      the deferred rule from shipping as untested dead code (PA-08).
- [ ] Conversion factor ≤ 0 rejected (R-13).
- [ ] Unit homonymous with the base unit and factor ≠ 1 rejected (R-13).
- [ ] Marking a new default leaves exactly one marked (R-14).
- [ ] Category name normalization and case-insensitive comparison (R-02).

**Integration tests** (`*IT`, Testcontainers — suffix mandatory, see CLAUDE.md)

- [ ] Full category cycle: create, edit, paginated list, disable, enable.
- [ ] `409 category_in_use` when disabling a category with an active product; success when its only
      products are inactive (R-04).
- [ ] `409 category_inactive` when creating a product under an inactive category and when
      re-enabling a product whose category is inactive (R-05, R-11).
- [ ] Full product cycle: create with inline units, edit, disable, enable, read by `external_id`.
- [ ] `409 duplicate_sku` on both create and edit (R-06, R-09).
- [ ] `400 invalid_request` when `PUT /products/{externalId}` carries a `baseUnit` field, and no
      route in the published OpenAPI document mutates a base unit (§6.2, PA-08).
- [ ] **Schema-level rejection of a second default sale unit**: a direct insert/update through the
      repository is refused by `uq_product_units_single_default` and surfaces as a conflict, not a
      500 (R-14, S-3).
- [ ] RBAC: `OPERATOR` and `BRANCH_MANAGER` receive `403` on every §6 mutation endpoint, and `200`
      on every read endpoint (R-01).
- [ ] `401` with no token on any endpoint of the module.
- [ ] Listing defaults to active-only; `active=false` and `active=all` behave per R-12; `active=maybe`
      returns `400`.
- [ ] The page cap applies with `size=5000`, and `sort` outside the allow-list returns `400` (R-12).
- [ ] Every endpoint's response **contains no numeric `id`** (explicit assertion over the JSON body,
      §7.1).
- [ ] Disabling a product with stock leaves the balances intact (R-10).
- [ ] Every mutation writes its `audit_logs` entry with a null `branch_id`, and an audit failure
      rolls the mutation back (R-15).

**Manual review**

- [ ] No new class in a direct subpackage of `com.optiplant.inventory` other than `catalog/`
      (CLAUDE.md rule).
- [ ] `catalog/domain/**` imports neither `org.springframework..` nor `jakarta.persistence..`.
- [ ] `catalog` imports no class from another business module; the stock-presence port lives in
      `shared` and `shared` still imports no module (§2.2).
- [ ] The stock-presence port declares **one** boolean method and no stock-shaped return type
      (§2.2, PA-09).
- [ ] No `catalog` adapter issues SQL against `branch_inventories` or `kardex_movements` (§2.2,
      rejected alternative 2 — ArchUnit cannot catch this).
- [ ] The §6 endpoints appear in `/v3/api-docs` with the error envelope documented.

---

## 12. Resolved decisions

**Every open question is closed.** PA-01 … PA-07 were resolved by the user; PA-08 … PA-11 by the
coordinator. Nothing in this contract is left for the designer to decide except the one point
deliberately delegated in §2.4 (where authorization rules live).

### 12.1. The eleven questions, as resolved

| ID | Resolution | Where it landed |
| :--- | :--- | :--- |
| **PA-01** | English. | Whole document; `docs/` untouched. `design.md` and `tasks.md` follow. |
| **PA-02** | `base_unit` **mutable** under a no-history precondition. | R-08 + §2.2 (`shared` inbound port, fail-closed, two rejected alternatives). |
| **PA-03** | Keep re-enable. | R-11, extended to categories for symmetry. |
| **PA-04** | Add the partial unique index. | S-3, R-14, new schema-validator checks, new `*IT`. |
| **PA-05** | `categories` gets logical disable. | S-1, S-2, R-03, R-04, R-05; physical deletion removed from the whole surface. |
| **PA-06** | Audit catalog mutations. | R-15. |
| **PA-07** | Listings return active only by default, uniformly. | R-12, §6.1, §6.2. |
| **PA-08** | **Defer the HTTP exposure** of the base-unit change; keep the rule and the port. | §1.1 (out of scope), §2.2 (delivered vs. deferred), R-08 (scenarios restated over the domain), §6.2 (endpoint removed, `PUT` rejects `baseUnit`), §7 (two error codes withdrawn), §8, §11. |
| **PA-09** | **Narrow port**: one boolean method, single purpose. | §2.2, §7.1 point 4, §11 manual review. |
| **PA-10** | One default for every role; no role-dependent branching. | R-12, §6.1, §6.2, and the note below. |
| **PA-11** | No cascade on category disable; the dangling state is accepted. | R-04, R-11, and the note below. |

### 12.2. Two accepted behaviours worth knowing before reading the code

Neither is technical debt. Both are decisions with a consequence someone will eventually notice and
mistake for a bug, so they are written down rather than discovered.

1. **A disabled category may still hold inactive products (PA-11).** Those products point at a
   disabled category, and R-11 refuses to re-enable them until the category is re-enabled first.
   No cascade exists: one click must not disable an unbounded number of products, and the R-11
   guard already prevents the inconsistent state from being re-entered. The dangling state is
   fully recoverable — re-enable the category, then the products.
2. **Listings hide inactive rows from everyone, `ADMIN` included (PA-10).** The management UI is
   expected to send `active=all` explicitly. A role-dependent default was rejected: the same
   request returning different result sets for different callers is a surprise inside a shared
   contract, and it would make every listing test role-dependent for no gain.

### 12.3. Points where this contract reads the instruction rather than transcribing it

Flagged because they are the places a reviewer should look first:

1. **`DELETE /categories/{externalId}` became `PATCH /disable` + `PATCH /enable`.** PA-05 fixed the
   semantics (logical disable, no physical deletion); the verb follows `iam`'s established
   precedent, because a `DELETE` that does not delete misrepresents the contract to every client
   and to the OpenAPI document.
2. **`category_in_use` was re-scoped rather than removed.** It now guards *disabling* a category
   that still has **active** products; a companion code `category_inactive` guards the opposite
   direction (attaching a product to a disabled category). Both are needed: without the second one,
   PA-05 would allow exactly the inconsistency the first one prevents.
3. **PA-08 was applied to the HTTP surface only.** The instruction was to defer the endpoint while
   keeping the rule and the port, so R-08's scenarios were rewritten over the *domain operation*
   rather than deleted, and the two base-unit error codes were withdrawn because nothing can raise
   them any more. `PUT /products/{externalId}` now **rejects** a `baseUnit` field with
   `invalid_request` instead of silently dropping it: a client that sends it must learn the change
   did not happen.
4. **"Update the seed" turned out to be unnecessary** for both schema changes — verified against the
   seed file, not assumed (§1.2). The instruction anticipated seed edits; the explicit column list
   at `02-seed-data.sql:45` and the one-default-per-product distribution at `:58-65` make them
   moot.
