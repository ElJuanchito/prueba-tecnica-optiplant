package com.optiplant.inventory.shared.stock;

import java.util.UUID;

/**
 * Inbound port {@code catalog} consumes to check R-08's precondition without importing
 * {@code inventory}: the implementation ships later as an {@code inventory} adapter, so
 * the module graph gains only {@code catalog -> shared <- inventory} and no cycle
 * (contract §2.2). Framework-free and trafficking only in an {@code external_id}-shaped
 * {@link UUID}, so {@code sharedEsUnaHoja} keeps holding.
 *
 * <p>A product is <em>untouched</em> when <strong>(a)</strong> it has no
 * {@code branch_inventories} row with a non-zero {@code current_stock},
 * {@code reserved_stock} or {@code in_transit_stock}, <strong>and (b)</strong> it has no
 * {@code kardex_movements} row at all, in any branch, ever.
 *
 * <p>Clause (b) is not redundant: a product whose stock has returned to zero still has
 * history — quantities, unit costs and running balances — recorded in the <em>old</em>
 * base unit, and RN-13 exists to stop a base-unit change silently reinterpreting it.
 *
 * <p>One consumer ({@code catalog}), one implementer
 * ({@code inventory}'s {@code InventoryStockPresenceAdapter}, DT-07). This interface
 * MUST NOT grow a stock-shaped return type or a second method. Absent any bean the
 * domain rule treats it as "cannot prove the product is untouched" and refuses the
 * change — it MUST NOT fail open.
 */
public interface ProductStockPresencePort {

	boolean isProductUntouched(UUID productExternalId);
}
