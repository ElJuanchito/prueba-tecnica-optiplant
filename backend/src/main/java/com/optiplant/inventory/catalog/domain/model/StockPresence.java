package com.optiplant.inventory.catalog.domain.model;

/**
 * The three answers R-08 must distinguish before a base-unit change (design §3.2,
 * D-3). It is deliberately an enum and not a boolean: "the stock-presence port
 * could not answer" is a first-class case, not the absence of one.
 *
 * <ul>
 *   <li>{@link #UNTOUCHED} — the product has no {@code branch_inventories} balance
 *       and no {@code kardex_movements} row, so the change is safe;</li>
 *   <li>{@link #HAS_HISTORY} — the product has stock or movements recorded in the
 *       old base unit; RN-13 forbids reinterpreting them, so the change is
 *       refused;</li>
 *   <li>{@link #UNKNOWN} — no implementation of
 *       {@code shared/stock/ProductStockPresencePort} is available (the state of
 *       this change), so the precondition cannot be proven and the change is
 *       refused. Fail closed — contract §2.2 is emphatic it must not fail open.</li>
 * </ul>
 *
 * <p>{@code BaseUnitChangePolicy} consumes this enum directly; the mapping from
 * the {@code Optional<ProductStockPresencePort>} bean to one of these values lives
 * in {@code ProductAdminService} (design §5.2), so no {@code Optional} whose
 * {@code orElse} could silently become "untouched" ever reaches the domain.
 */
public enum StockPresence {
	UNTOUCHED,
	HAS_HISTORY,
	UNKNOWN
}
