# Apply Progress: `add-catalog-module`

## Phase 1 — S1: Schema, validator and the `shared` port (PR1)

**Mode**: Standard (openspec, `strict_tdd: false`).
**Branch**: `feat/ep-02-catalog-02-s1-esquema`.
**Status**: 13/13 tasks complete. Ready for verify.

### Completed tasks

- [x] 1.1 `categories` gains `is_active BOOLEAN NOT NULL DEFAULT TRUE` and `updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` — table block now matches design §10.1 edit 1 verbatim.
- [x] 1.2 `CREATE UNIQUE INDEX uq_categories_name_ci ON categories (LOWER(name));` added right after `idx_categories_external_id`, with the four-line Spanish comment from design §10.1 edit 2. The column's pre-existing `UNIQUE` on `name` is kept (change stays additive).
- [x] 1.3 `CREATE UNIQUE INDEX uq_product_units_single_default ON product_units(product_id) WHERE is_default_sale_unit;` added after `idx_product_units_product`, with its Spanish comment (design §10.1 edit 3). `idx_product_units_product` already existed at `:117`, so only the partial unique index is new.
- [x] 1.4 `02-seed-data.sql` NOT edited. Verified: `:45` inserts categories with explicit list `(external_id, name, description)` so S-1/S-2 take defaults; `product_units` `:58-65` has exactly one `is_default_sale_unit = TRUE` per product (1→SACO_50KG, 2→GALON_4L, 3→BOLSA_INDIVIDUAL, 4→PAQUETE_1KG, 5→ROLLO_500M); the four category names at `:46-49` have no case-insensitive collision.
- [x] 1.5 Section `G. Catálogo maestro` appended to `scripts/validar_esquema.sh` after section F, before the summary block, with the five checks verbatim from design §10.3 (2×`igual`, 2×`rechaza`, 1×`acepta`). Check count 25 → 30.
- [x] 1.6 `CLAUDE.md:15` "25 invariantes" → "30 invariantes"; `openspec/config.yaml:46` "Checks 25 invariants" → "Checks 30 invariants".
- [x] 1.7 `./scripts/validar_esquema.sh` run against a fresh `postgres:17-alpine` container — 30/30 checks green (see gate output below). `igual "20 tablas creadas"` still `(20)`; `02-seed-data.sql` loads; section G all green.
- [x] 1.8 `docs/diagrama_er.md`: Mermaid `CATEGORIES` block gains `boolean is_active`; PlantUML `categories` entity gains `* is_active : BOOLEAN` and `updated_at : TIMESTAMPTZ` (mirrors the existing `products` entity style). Spanish untouched, no `RF`/`RNF`/`RN` identifier introduced.
- [x] 1.9 `backend/src/main/java/com/optiplant/inventory/shared/stock/ProductStockPresencePort.java` created — one method `boolean isProductUntouched(UUID productExternalId)`, sole import `java.util.UUID`, Javadoc pins the exact two-clause `(a)`/`(b)` predicate of contract §2.2 (including the non-redundancy of clause (b) / RN-13) and the fail-closed / single-question (PA-09) constraints.
- [x] 1.10 `shared/audit/AuditAction.java`: enum now `CREATE, UPDATE, DISABLE, ENABLE, DELETE`; Javadoc rewritten to say it holds generic CRUD verbs while `AuditEntryCommand.action()` stays a `String` for module-specific names, and that no `switch` dispatches on it so adding constants is safe (design D-9).
- [x] 1.11 `cd backend && ./mvnw test -Dtest=SharedIsFrameworkFreeTest,ModuleBoundariesTest` — BUILD SUCCESS, `ModuleBoundariesTest` 5/5 (includes `sharedEsUnaHoja`, `ningunModuloEntraAlInteriorDeOtro`), `SharedIsFrameworkFreeTest` 1/1, with the new `shared/stock` package present.
- [x] 1.12 `python3 scripts/validar_trazabilidad.py` — RESULTADO: trazabilidad íntegra (42 RF · 34 RNF · 17 RN · 37 CU · 8 DT), exit 0.
- [x] 1.13 `cd backend && ./mvnw verify` — BUILD SUCCESS.

### Files changed

| File | Action | What was done |
|------|--------|---------------|
| `backend/init-db/01-init-schema.sql` | Modified | S-1/S-2 columns on `categories`; `uq_categories_name_ci` (S-4); `uq_product_units_single_default` (S-3) |
| `scripts/validar_esquema.sh` | Modified | New section `G. Catálogo maestro`, five checks (25 → 30) |
| `CLAUDE.md` | Modified | Verificación line: 25 → 30 invariantes |
| `openspec/config.yaml` | Modified | schema-validator purpose: 25 → 30 invariants |
| `docs/diagrama_er.md` | Modified | `is_active` in Mermaid `CATEGORIES`; `is_active` + `updated_at` in PlantUML `categories` |
| `backend/src/main/java/com/optiplant/inventory/shared/stock/ProductStockPresencePort.java` | Created | Inbound stock-presence port, JDK-only, leaf |
| `backend/src/main/java/com/optiplant/inventory/shared/audit/AuditAction.java` | Modified | `+ ENABLE, DELETE`; Javadoc extended |
| `openspec/changes/add-catalog-module/tasks.md` | Modified | Phase 1 tasks 1.1–1.13 marked `[x]` |

### Final gate output (run, not asserted from memory)

**`./scripts/validar_esquema.sh`** — exit 0:

```
G. Catálogo maestro
  ok     toda categoría tiene estado de actividad  (0)
  ok     las categorías sembradas nacen activas  (4)
  ok     RN-13 · un producto no puede tener dos unidades de venta predeterminadas
  ok     dos productos distintos tienen cada uno su unidad predeterminada
  ok     el nombre de una categoría es único sin distinguir mayúsculas
------------------------------------------------------------
RESULTADO: 30 comprobaciones correctas — esquema íntegro
```

`A. ... ok 20 tablas creadas (20)` and `ok 02-seed-data.sql` both still pass.

**`python3 scripts/validar_trazabilidad.py`** — exit 0:

```
RESULTADO: trazabilidad íntegra
  42 RF · 34 RNF · 17 RN · 37 CU · 8 DT
```

**`cd backend && ./mvnw verify`** — BUILD SUCCESS:

```
[INFO] Tests run: 67, Failures: 0, Errors: 0, Skipped: 0        (surefire)
[INFO] Tests run: 53, Failures: 0, Errors: 0, Skipped: 0        (failsafe / *IT)
[INFO] ModuleBoundariesTest      Tests run: 5, Failures: 0, Errors: 0
[INFO] SharedIsFrameworkFreeTest Tests run: 1, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS
```

One `ERROR` line appears in the log inside `AuditAtomicityIT` — it is the test's own deliberate `AtomicityFixtureFailure` exercising audit-write rollback, and the suite reports `Tests run: 2, Failures: 0, Errors: 0`.

### Deviations from design

None — implementation matches design §10.1–§10.4, §5.3 and D-9. `idx_product_units_product` was already present in the schema (`:117`), so task 1.3 added only the partial unique index, consistent with design §10.1 edit 3 which shows that `CREATE INDEX` line as existing context.

### Issues found

None. `design.md` and `tasks.md` were complete and correct for S1.

### Workload / PR boundary

- Mode: chained PR slice — PR1 of 8 (feature-branch-chain; PR1 targets the tracker branch).
- Current work unit: S1 — Schema + `shared`.
- Boundary: starts from the planning branch; ends with the schema edits, validator section G, ER-diagram sync, `shared/stock` port and `AuditAction` extension. No `catalog` consumer exists yet.
- Rollback: `git revert` of this commit + `docker compose down -v`. Nothing depends on the new port or enum constants.
