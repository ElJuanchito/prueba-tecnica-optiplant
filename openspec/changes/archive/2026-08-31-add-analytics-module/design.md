# Design — `add-analytics-module`

Step 2 of 3. `contract.md` is authoritative: `R-00…R-26`, `P-01…P-04`, `F-1…F-8`, `T-01…T-06`, `A-1…A-7`,
`PA-01…PA-06` and §5 … §9 are settled and are **cited, never restated**. This file decides only what the
contract and the nine shipped modules leave open. **~52 new classes** (48 in `analytics`, 3 in `shared`,
1 in `inventory`), **3 modified production classes** (`QueryStockUseCase`, `StockQueryService`,
`SecurityConfig`). Zero `backend/init-db/` edits (§2.5) — if a task seems to need one, §2.5 was wrong:
stop and report.

## 1. Placement and graph

Everything new lives under `com.optiplant.inventory.analytics`, except the P-03 port (in `shared`) and its
one implementation (in `inventory`). Nothing in a direct subpackage of the base package.
`ModuleBoundariesTest.MODULOS` already lists `analytics` (`:35-37`) — **no ArchUnit edit**.

```
analytics -> shared   (AuthenticatedPrincipal, Role, PrincipalAccessor, NetworkAvailabilityPort)
inventory -> shared   (implements NetworkAvailabilityPort — the RouteLeadTimePort direction)
```

No `analytics -> inventory` and no `analytics -> sales` edge exists: `analytics` reads their **tables**,
never their **types** (P-01). `shared` gains one package and imports no module name, so it stays a leaf.
The graph gains one node with one outgoing edge — no cycle is reachable.

## 2. The `shared` read port (P-03) — the only cross-module surface

**`shared/availability/NetworkAvailabilityPort`**, deliberately not `shared/inventory`: a `shared`
subpackage named after a module invites the reader to think that module leaked in (the `shared/route`
rationale, verbatim).

```java
public interface NetworkAvailabilityPort {
    /** Empty when {@code productExternalId} names no product — the caller owns the 404 (R-24). */
    Optional<NetworkAvailabilityView> networkAvailability(UUID productExternalId);
}

public record NetworkAvailabilityView(UUID productExternalId, String sku, String name,
        List<BranchAvailabilityView> branches, BigDecimal networkTotal) { }

public record BranchAvailabilityView(UUID branchExternalId, String branchName, BigDecimal currentStock,
        BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock) { }
```

**D-1 — `shared` declares its own view records rather than reusing `inventory`'s `NetworkAvailability` /
`BranchAvailability`.** It has no choice: `shared` is a leaf, so it cannot import an `inventory` type, and
`analytics` cannot either. The two record pairs are field-identical minus `isOwnBranch`, which is exactly
P-03/R-24's point — an external system has no branch, so the marker is **absent from the port's type**, not
nulled at the edge. Reversal: none available without breaking boundary rule 5.

**D-2 — the port returns `Optional.empty()`, it does not throw.** `inventory`'s `ProductNotFoundException`
is an `inventory` type; catching it in `analytics` would be the forbidden edge. `analytics` throws its own
`ProductNotFoundException` → `404 product_not_found` (R-24, §7).

**D-3 — `QueryStockUseCase` gains an actor-free overload**
`NetworkAvailability networkAvailability(UUID productExternalId)`, and the existing
`networkAvailability(AuthenticatedPrincipal, UUID)` becomes `mark(unmarked, ownBranch)` over it
(`StockQueryService:62-80`, read). The `inventory`-side adapter then delegates to a real use case instead of
fabricating a principal to satisfy a signature. **Zero logic is restated** (P-03/R-23): the product lookup,
the per-branch rows, the `availableStock` semantics and the `networkTotal` sum stay where they are.
Reversal: inline the overload, one method.

**New — `inventory/infrastructure/adapter/out/availability/NetworkAvailabilityAdapter`** (`@Component`),
the single implementation, mapping `NetworkAvailability → NetworkAvailabilityView` and dropping
`isOwnBranch`. It depends on `QueryStockUseCase` (a `port/in`), which is `infrastructure → application` —
allowed; rule 2 forbids the reverse only. `analytics` injects `NetworkAvailabilityPort` straight into its
service, as `DispatchTransferService` injects `RouteLeadTimePort`; it declares no adapter of its own.

## 3. How a module with no `@Entity` reads (P-01)

**D-4 — `analytics` reads through `org.springframework.jdbc.core.simple.JdbcClient`, not Spring Data.**
`inventory`'s `ForeignKeyResolverSpringDataRepository extends Repository<BranchInventoryJpaEntity, Long>`
trick is unavailable here: Spring Data JPA's repository factory resolves the domain type as a JPA-managed
type, and every `@Entity` in the persistence unit belongs to another module — importing one is the edge
`analytics -> inventory`. `JdbcClient` needs no domain type at all.

Verified, not assumed: `spring-jdbc:7.0.9` and `spring-boot-jdbc:4.1.1` are on the compile classpath
(`./mvnw dependency:list`), and `org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration`
is listed in that jar's `AutoConfiguration.imports`, so the `JdbcClient` bean exists with no configuration.
**One claim is left to execute in S2, not asserted here:** that `JdbcClient` joins the `readOnly = true`
transaction `JpaTransactionManager` opened (it should, through `DataSourceUtils` over the `DataSource` the
manager derives from the `EntityManagerFactoryInfo`). Correctness does not depend on it — every endpoint is
one statement over committed data (T-03) — but the DoD's `readOnly` claim does. Reversal of D-4:
`EntityManager.createNativeQuery` with a `Tuple` transformer, adapter-local.

Each adapter maps rows with a private `RowMapper` into a domain record. Projection interfaces are a Spring
Data feature and do not apply.

## 4. The seven queries — table, grouping, filters

All live in `analytics/infrastructure/adapter/out/persistence`. Every `sales` aggregation carries
`s.status = 'COMPLETED'` (F-2/R-03); **none mentions `kardex_movements`** (F-4/PA-06/A-7). Branch and
product identity enter as `external_id` and are resolved in the same statement by joining `branches` /
`products`, so no numeric id ever crosses an adapter boundary.

| # | Query (method) | Tables | Grouping / ordering | Filters |
| :--- | :--- | :--- | :--- | :--- |
| Q-1 | `SalesAnalyticsJdbcAdapter.monthlySales` (A-4) | `sales s` ⋈ `sale_items si ON si.sale_id = s.id` ⋈ `branches b` | `GROUP BY date_trunc('month', s.created_at)`, `ORDER BY 1 ASC` | `b.external_id = :branch`, `s.status='COMPLETED'`, `s.created_at >= :from AND < :to` |
| Q-2 | `SalesAnalyticsJdbcAdapter.rotation` (A-5) | `sales s` ⋈ `sale_items si` ⋈ `products p` ⋈ `branches b` ⋈ `branch_inventories bi ON (bi.branch_id = s.branch_id AND bi.product_id = si.product_id)` | CTE `ranked`: `GROUP BY p.id`, then `SUM(salesAmount) OVER (ORDER BY salesAmount DESC, p.sku ASC)` and `SUM(salesAmount) OVER ()`; outer `ORDER BY` per `direction` | idem Q-1 plus `p.is_active` |
| Q-3 | `SalesAnalyticsJdbcAdapter.rotationCount` | as Q-2's inner grouping | — | idem — the page envelope's `totalElements` |
| Q-4 | `InventoryAnalyticsJdbcAdapter.replenishment` (A-1) | `branch_inventories bi` ⋈ `products p` ⋈ `branches b` | `ORDER BY` per `sort` (default `CASE WHEN bi.current_stock = 0 THEN 0 ELSE 1 END, p.sku`) | `b.external_id = :branch`, `p.is_active`, `bi.current_stock <= bi.min_stock_threshold`, optional `severity` |
| Q-5 | `TransferAnalyticsJdbcAdapter.activitySummary` (A-2) | `transfers t` ⋈ `branches` twice (origin, destination) | `GROUP BY t.status, side` | `t.status IN ('REQUESTED','IN_PREPARATION','IN_TRANSIT')`; delayed = `t.estimated_arrival_at < now() AND t.actual_arrival_at IS NULL` |
| Q-6 | `TransferAnalyticsJdbcAdapter.stockImpact` (A-2, F-7) | `transfer_items ti` ⋈ `transfers t` ⋈ `products p` ⋈ `branch_inventories bi` (LEFT, on the scoped branch) | `GROUP BY p.id`, `ORDER BY p.sku` | inbound = `SUM(ti.dispatched_quantity)` where `t.destination_branch_id = :b AND t.status='IN_TRANSIT'`; outbound = `SUM(ti.requested_quantity)` where `t.origin_branch_id = :b AND t.status IN ('REQUESTED','IN_PREPARATION')` |
| Q-7 | `BranchBoardJdbcAdapter.corporateBoard` (A-3, A-6) | `branches b` LEFT JOIN four scalar sub-selects: month sales over `sales`⋈`sale_items`; `SUM(bi.current_stock * bi.average_cost)` over `branch_inventories`; critical count over `branch_inventories`; active-transfer count over `transfers` | `GROUP BY b.id`, `ORDER BY` the `sort`/`direction` pair | `b.is_active` (F-8), `s.status='COMPLETED'`, the requested month |
| Q-8 | `BranchDirectoryJdbcAdapter.findActiveBranch` | `branches` | — | `external_id = :id AND is_active` → `404 branch_not_found` (R-02) |
| Q-9 | `ServiceUserJdbcAdapter.findActiveServiceUser` | `users` | — | `external_id = :id AND is_active` — the F-6 filter's subject |

**D-5 — R-09's ABC classification is a SQL window over the whole period, but the 80/95 thresholds live in
Java.** The cumulative share genuinely cannot be computed page by page, so `SUM(...) OVER (ORDER BY
salesAmount DESC)` in Q-2's CTE is not an optimisation, it is the requirement; but the cut-points are a
business rule with a worked boundary (exactly 80 %, exactly 95 %) and belong in a testable pure function,
`AbcClassifier.classify(cumulativeSharePercent)`. SQL computes the arithmetic that needs the whole set;
Java owns the rule. Reversal: a `CASE` in Q-2, at the cost of a second definition.

**D-6 — `direction=BOTTOM` reverses presentation only; the ranking that produces `abcClass`,
`sharePercent` and `cumulativeSharePercent` is always by `salesAmount DESC`.** Ranking ascending would make
the worst-selling product class `A`, which inverts R-09. The outer `ORDER BY` flips; the CTE never does.
The tie-breaker is `p.sku ASC` in both directions, so pagination is stable (R-09's "stable across pages").

**D-7 — `coverageDays` is computed in Java (`CoveragePolicy`), never in SQL.** R-10 and R-15 give it three
outcomes SQL would express as nested `CASE`: `0` when stock is zero, `null` when demand is zero (never
infinity), otherwise `currentStock ÷ (unitsSold ÷ periodDays)` at scale 2. It is the same function for
rotation and for replenishment, so it exists once.

**D-8 — `ReplenishmentSeverity.of(currentStock)` is the domain rule, and Q-4 repeats its boundary in an
`ORDER BY CASE`.** Deliberate duplication, and the only one in this design: R-16's default sort is over the
whole filtered set, so it must be a SQL `ORDER BY`, and a sort key cannot be derived from a value the page
has not fetched yet. `ReplenishmentPanelIT` pins that the SQL order and the Java rule agree.

**Not-taken shortcut, worth naming:** Q-1 does not use `generate_series` to fill absent months. R-04/R-06
need a contiguous window with explicit zeroes and R-05 needs the `null` variation for a zero previous
month; both are assembled by `SalesTrendPolicy` in the domain, where a unit test reaches them without
Docker.

## 5. Domain — thin, and honestly so

`analytics` computes no business state, so its domain layer is **records plus five pure functions**. No
value object is invented to pad it: there is no `SalesAmount` wrapper, no `Percentage` type, no aggregate
root. `BigDecimal` and `UUID` carry the values, exactly as the read-side records of `purchases` do. Nothing
here is mutable and nothing has a setter, but that is because everything is a query result — not because an
invariant is being protected.

**`domain/model`** — `MonthlySales(int year, int month, long salesCount, BigDecimal unitsSold, BigDecimal
totalAmount)` · `SalesTrend(UUID branchExternalId, List<MonthlySales> months, BigDecimal
monthOverMonthVariationPercent, boolean empty)` · `RotationLine(UUID productExternalId, String sku, String
name, BigDecimal unitsSold, BigDecimal salesAmount, BigDecimal sharePercent, BigDecimal
cumulativeSharePercent, AbcClass abcClass, BigDecimal coverageDays)` · `AbcClass {A, B, C}` ·
`RotationDirection {TOP, BOTTOM}` · `TransferStatusCounts(long requested, long inPreparation, long
inTransit)` · `TransferActivitySummary(TransferStatusCounts inbound, TransferStatusCounts outbound, long
delayedCount)` · `TransferStockImpact(...)` (R-13's seven fields) · `ReplenishmentLine(...)` ·
`ReplenishmentSeverity {OUT_OF_STOCK, CRITICAL}` with `of(BigDecimal currentStock)` · `BranchPerformance`
(R-20's nine fields) · `AnalyticsPeriod(Instant from, Instant to, int periodDays)` ·
`AnalyticsPage<T>(List<T> content, long totalElements, int page, int size)` — one generic page record, as
`PurchasePage<T>` already is.

**`domain/service`** — `AbcClassifier` (D-5, R-09) · `CoveragePolicy` (D-7, R-10/R-15) · `SalesTrendPolicy`
(R-04 zero-fill, R-05 variation and its `null` branch, R-06 `empty`) · `RotationPageAssembler` (joins Q-2's
raw rows to `AbcClassifier` and `CoveragePolicy`) · `AnalyticsAccessPolicy` (§6). All pure Java: no Spring,
no Jakarta.

**`domain/exception`** — four, one per §7 code bean validation cannot raise:
`BranchContextRequiredException`, `CrossBranchAccessDeniedException`, `BranchNotFoundException`,
`ProductNotFoundException`. Each module declares its own — the precedent `inventory`, `transfers`, `sales`
and `purchases` all follow.

**No domain event, no domain service that mutates, no `Draft`, no `with*` copy** (R-01, P-02, T-04).

## 6. Authorization — where R-02 lives

**`AnalyticsAccessPolicy.resolveBranch(AuthenticatedPrincipal actor, UUID requestedBranchExternalId)`** —
pure Java, the whole of R-02 in one ordered function, and the order is the security property:

1. `actor.role() != ADMIN` **and** `requested != null` ⇒ `CrossBranchAccessDeniedException` →
   `403 cross_branch_access_denied`. **First**, before any lookup, so no probe distinguishes a real branch
   from an invented one (§7 "must not leak").
2. `actor.role() != ADMIN` ⇒ return `actor.branchId()`.
3. `ADMIN` with `requested != null` ⇒ return `requested` — the caller then validates it exists and is
   active through `BranchDirectoryPort` (Q-8) ⇒ `404 branch_not_found`.
4. `ADMIN` with `requested == null` and `actor.branchId() != null` ⇒ that branch (a branch-assigned `ADMIN`,
   §5's last sentence).
5. `ADMIN` with `requested == null` and `branchId == null` ⇒ `BranchContextRequiredException` →
   `403 branch_context_required`, its message naming `/api/analytics/corporate/branches` (PA-01).

The four branch dashboards call it; the corporate board does not (it accepts no `branchExternalId`, R-20)
and CU-EXT-01 does not (no actor at all, R-24). **There is no shared branch-scope component to reuse:**
`shared` provides `PrincipalAccessor` (read the principal) and `AuthenticatedPrincipal.isCorporate()`, and
every module writes its own access policy over them — `TransferAccessPolicy`, `PurchaseAccessPolicy`,
`InventoryAccessPolicy`. `AnalyticsAccessPolicy` is the fourth, and it is read-only, so it never asks
`mayMutateBranch`.

**D-9 — the corporate board's `ADMIN` check is a `SecurityConfig` matcher, and `iam` gains an
`AccessDeniedHandler`.** §5 wants `hasAuthority("ADMIN")` and §7 wants `403 forbidden` in the uniform
`{code, message}` envelope; today no `AccessDeniedHandler` exists in the codebase (grepped), so Spring
Security answers a matcher denial with an **empty** body. One bean in `iam/infrastructure/config`, wired
with `.exceptionHandling(e -> e.accessDeniedHandler(...))`, writes `{"code":"forbidden", …}`. This changes
every existing matcher denial from an empty 403 to a 403 with a body — additive, but S2 must grep the
archived `*IT` for an assertion on an empty 403 body before shipping it.

## 7. Adapters — web and security

**Controllers** (`infrastructure/adapter/in/web`), request/response records nested as `SaleController`
does. `AnalyticsDashboardController` at `/api/analytics` (`/dashboard/sales-trend`, `/dashboard/rotation`,
`/dashboard/transfers`, `/dashboard/transfers/stock-impact`, `/replenishment`) ·
`CorporateBoardController` at `/api/analytics/corporate/branches` · `ExternalAvailabilityController` at
`/api/external/availability/{productExternalId}`. §6 verbatim; no numeric id in any field; no `POST`, `PUT`,
`PATCH` or `DELETE` mapping anywhere in the module (R-01, greppable).

**Pagination** reuses `TransferController`'s exact helper (`:153-161`), copied per controller as every
module does: `DEFAULT_PAGE_SIZE = 20`, `MAX_PAGE_SIZE = 100`, and `resolveSize` **throws**
`IllegalArgumentException` for `size < 1 || size > 100`, which `AnalyticsExceptionHandler` maps to
`400 invalid_request` — never `catalog`'s silent clamp (R-00, DT-10). `page < 0` is `Math.max(page, 0)`,
matching the shipped controllers. `months`, the `from`/`to` window (R-11), and the `sort` / `direction` /
`severity` enums are validated the same way, in the controller, before any query runs (RNF-SEC-05).

**`AnalyticsExceptionHandler`** — `@RestControllerAdvice(basePackages =
"com.optiplant.inventory.analytics.infrastructure.adapter.in")`, the whole `in` package, mapping
`IllegalArgumentException` / `MethodArgumentTypeMismatchException` → `400 invalid_request`, and the four
domain exceptions to their §7 codes. `forbidden` comes from D-9's handler, `invalid_api_credential` from
the filter — every §7 code is reachable, none is dead.

**`SecurityConfig`** (`iam`) gains two matchers, string literals only, **before** the final
`anyRequest().authenticated()` and in this order:

```java
.requestMatchers("/api/analytics/corporate/**").hasAuthority("ADMIN")   // R-19
.requestMatchers("/api/analytics/**").authenticated()                    // §5: all three roles
```

Corporate first, or the general rule swallows it — the trap `catalog`, `transfers`, `sales` and `purchases`
each documented in place.

**The API-key path (F-6, PA-05).** Four classes under `analytics/infrastructure/config`, the shape of
`sales`' four, imported from nothing:

- `ExternalAvailabilityApiKeyProperties` — `@ConfigurationProperties(prefix = "optiplant.analytics.external")`,
  `List<ApiKeyEntry(String key, UUID userExternalId)>` and `findMatchingEntry` comparing with
  `MessageDigest.isEqual` over UTF-8 bytes, iterating **every** entry so the timing does not leak the match
  position. **No `branchExternalId`** — unlike `sales`' entry, CU-EXT-01 is network-wide (R-24), so the
  principal's `branchId` is `null`.
- `ExternalAvailabilityAuthenticationToken` — the `AuthenticatedPrincipal` carrier.
- `ExternalAvailabilityApiKeyFilter extends OncePerRequestFilter` — `X-Api-Key`; absent, blank, unmatched,
  or matched to a user `ServiceUserPort` (Q-9) does not return active ⇒ its own `401` JSON body
  `{"code":"invalid_api_credential", …}`, written directly because the filter runs before
  `DispatcherServlet`. No branch of it says which half failed and none logs key material (R-25).
  **`shouldNotFilter` returns `true` unless the URI starts with `/api/external/availability`** — the filter
  is a `@Component`, which Boot also registers in the plain servlet filter chain, so without that guard it
  would run on every request in the application, `sales`' external path included.
- `ExternalAvailabilitySecurityConfig` — `@Bean @Order(2) SecurityFilterChain` with
  `securityMatcher("/api/external/availability/**")`, CSRF/basic/form disabled, `STATELESS`,
  `anyRequest().authenticated()`, `addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)`.
  **`@Order(2)`**, not `1`: `sales`' chain holds `@Order(1)` with `securityMatcher("/api/external/sales/**")`
  and IAM's chain carries no matcher at the default lowest precedence. Two exact matchers cannot shadow one
  another, and both precede IAM's catch-all.

## 8. Use cases and ports

**Primary (`application/port/in`)** — six, and every implementation is annotated
`@Transactional(readOnly = true)` (T-01), takes `AuthenticatedPrincipal actor` except the last, and writes
nothing:

| Port | Method(s) | CU | Actor / authorization |
| :--- | :--- | :--- | :--- |
| `QuerySalesTrendUseCase` | `salesTrend(actor, SalesTrendQuery)` | CU-DSH-01 | three roles, `resolveBranch` |
| `QueryProductRotationUseCase` | `rotation(actor, RotationQuery)` | CU-DSH-01 | three roles, `resolveBranch` |
| `QueryTransferActivityUseCase` | `summary(actor, UUID)` · `stockImpact(actor, StockImpactQuery)` | CU-DSH-01 | three roles, `resolveBranch` |
| `QueryReplenishmentUseCase` | `replenishment(actor, ReplenishmentQuery)` | CU-DSH-02 | three roles, `resolveBranch` |
| `QueryCorporateBoardUseCase` | `corporateBoard(CorporateBoardQuery)` | CU-DSH-03 | `ADMIN` only, by matcher (D-9); no branch resolution |
| `QueryNetworkAvailabilityUseCase` | `availability(UUID productExternalId)` | CU-EXT-01 | none — network-wide (R-24) |

Every query record carries `page`/`size` already validated and **`branchExternalId` only where §6 allows
it**; the services never read a branch from anywhere but `AnalyticsAccessPolicy`.

**Secondary (`application/port/out`)** — five, named for the need, none of them a write:
`SalesAnalyticsPort` (Q-1, Q-2, Q-3) · `InventoryAnalyticsPort` (Q-4) · `TransferAnalyticsPort` (Q-5, Q-6) ·
`BranchBoardPort` (Q-7) · `BranchDirectoryPort` (Q-8). `ServiceUserPort` (Q-9) is declared under
`infrastructure/config` alongside its only consumer, the filter — it serves authentication, not a use case,
and putting it in `application/port/out` would claim a use case depends on it. `NetworkAvailabilityPort`
comes from `shared` and is injected directly (§2). **No `application/port/out` interface has a method that
returns void or takes a command** — the shape a reviewer can check for P-02 at a glance.

## 9. Transaction boundaries

| Operation | Transaction | Locks | Isolation | `AFTER_COMMIT` |
| :--- | :--- | :--- | :--- | :--- |
| All seven endpoints | one `@Transactional(readOnly = true)` per service method (T-01) | **none** — no `FOR UPDATE`, no `LockModeType`, no advisory lock (T-02, RN-09) | READ COMMITTED | **none** — no commit to hang one on (T-04) |

Nothing is atomic with anything (T-03): a dashboard is a snapshot. The P-03 port call is a plain
synchronous read inside the same transaction — never `@Async`, never an event (T-06). Every method is `GET`
and idempotent (T-05).

## 10. Persistence, schema and technical debt

`01-init-schema.sql` and `02-seed-data.sql` are **not** edited (§2.5), no index is added (F-3, F-4, A-1),
so `docs/diagrama_er.md` needs no edit and `./scripts/validar_esquema.sh` must stay green **and
unaffected**; if it must change, §2.5 was wrong: stop and report.

**`DT-14` is written in S3** (contract §9.2/§10 PR 3) into `docs/deuda_tecnica.md` — summary row in §2,
detail card in §3 after `DT-13` (the document has no changelog section — verified) — in Spanish, severity Media, status Aceptada,
origin "contrato del módulo `analytics`". Its pay-off plan is §9.2's verbatim: at the next schema change
(`DT-01`'s Flyway migration is the carrier), `CREATE INDEX ON sales(branch_id, created_at) INCLUDE
(total_amount)` and `CREATE INDEX ON sale_items(sale_id) INCLUDE (product_id, quantity, subtotal)`, then a
nightly rollup table if still short. `validar_trazabilidad.py` must then report **14 DT declared, 14 with
fiche** — it is the one document edit in this change, and no `RF`/`RNF`/`RN`/`CU` row moves.

**What S3 actually measures** (§9.2's "at risk" verdicts are hypotheses until executed): A-4 (Q-1, one
branch, 4 months), A-5 (Q-2, one branch, one month) and A-6 (Q-7, all branches, one month). Timing them
against the Testcontainers PostgreSQL with the seeded volume is not RNF-PER-01 proof — the seed is nowhere
near §5.1's reference load — so the S3 step is `EXPLAIN (ANALYZE, BUFFERS)` on those three statements,
recorded in the PR description, confirming that Q-1/Q-2 drive from `idx_sales_branch_date` and that Q-7
joins `sale_items` **once**, not once per branch and not once per page. A nested-loop over 150 000 sales
appearing per branch is the failure mode `DT-14` predicts and the one thing this step exists to catch.

## 11. Decisions taken here, and their reversal cost

| # | Decision | Reversal |
| :--- | :--- | :--- |
| D-1 | `shared` declares its own availability view records (§2) | none available — boundary rule 5 |
| D-2 | The `shared` port returns `Optional.empty()`; `analytics` owns the 404 (§2) | none — the alternative is the forbidden edge |
| D-3 | `QueryStockUseCase` gains an actor-free `networkAvailability(UUID)` overload (§2) | inline it, one method |
| D-4 | `analytics` reads with `JdbcClient`, not Spring Data (§3) | `EntityManager.createNativeQuery`, adapter-local |
| D-5 | Cumulative share is a SQL window; the 80/95 cut-points are Java (§4) | a `CASE` in Q-2, at the cost of a second definition |
| D-6 | `BOTTOM` reverses presentation only; ranking is always `salesAmount DESC` (§4) | none — reversing the rank inverts R-09 |
| D-7 | `coverageDays` is `CoveragePolicy`, never SQL (§4) | a nested `CASE`, untestable without Docker |
| D-8 | `ReplenishmentSeverity`'s boundary is repeated in Q-4's `ORDER BY` (§4) | none — a sort key cannot come from an unfetched page |
| D-9 | The corporate `ADMIN` gate is a matcher, and `iam` gains an `AccessDeniedHandler` (§6) | enforce in the service and drop the handler, losing §5's matcher |
| D-10 | `ServiceUserPort` lives beside the filter, not in `application/port/out` (§8) | move it, one package |
| D-11 | The external chain is `@Order(2)` with an exact `securityMatcher` (§7) | none — `@Order(1)` is taken |

**Rejected.** An `@Entity` over any foreign table (P-01 — two owners for one table). A materialised rollup
or snapshot table (§1, §2.5 — a migration). A covering index for A-4/A-5/A-6 (§2.5 — `DT-14` records it
instead). Any read of `kardex_movements` (F-4, PA-06). A composite dashboard endpoint (PA-04). Importing
`sales`' `ExternalApiKeyProperties` or promoting it to `shared` (PA-05 — the forbidden edge, or a refactor
of an archived module for ~80 mechanical lines). A turnover ratio derived from current stock (PA-03 — a
number that looks precise and is not). Caching (§1, RNF-DIS-01). Clamping an oversized page (DT-10).

## 12. Traps specific to this change

1. **`sale_items` has no `branch_id` and no date column** (F-1). Every item aggregation must reach the
   branch and the period through `sales`; a `WHERE si.branch_id` does not compile against this schema.
2. **`status = 'COMPLETED'` on every `sales` aggregation** (F-2/R-03). Omitting it inflates Q-1, Q-2 and
   Q-7 by every voided sale, and the test that catches it is the one that voids a sale and re-reads.
3. **`idx_branch_inventory_critical` does not serve `current_stock <= min_stock_threshold`** (F-3). Do not
   "fix" Q-4 by adding an index — §2.5 forbids the schema change, and A-1 already ruled the scan sufficient.
4. **The ABC window runs over the whole period, never the page** (R-09/D-5). A `LIMIT` inside the CTE turns
   a classification into a per-page artefact and the bug survives every unit test.
5. **`shouldNotFilter` on the API-key filter** (§7). A `@Component` filter with no guard runs on every
   request in the application, `/api/external/sales` included, and answers `401` before `sales`' own filter
   is reached.
6. **Ship the six application services unannotated in S1** while their out-ports have no adapter — S2
   restores `@Service`; registering them in S1 breaks `ApplicationContextIT`, as in every module since
   `add-inventory-module`.
7. **D-9's `AccessDeniedHandler` changes every existing matcher denial's body.** Grep the archived `*IT`
   for an assertion on an empty 403 body before shipping it.
