package com.optiplant.inventory.shared.stock;

/**
 * The eight Kardex movement types (contract §2.2, P-02), one source of truth for the
 * {@code movement_type} literals of {@code kardex_movements} ({@code 01-init-schema.sql:215-226}).
 * Framework-free — no {@code org.springframework..}, no {@code jakarta.persistence..} —
 * so {@code sharedEsUnaHoja} and {@code SharedIsFrameworkFreeTest} keep holding.
 *
 * <p>The sign of a movement is a property of its type, never of the number carried
 * alongside it (P-02, RN-13): {@link #isInbound()} distinguishes the four types that add
 * to {@code current_stock} from the four that subtract.
 */
public enum StockMovementType {

	/** Goods receipt from a supplier — inbound, valued at a supplied cost (P-03). */
	PURCHASE_RECEIPT,
	/** Commercial sale — outbound, valued at the branch's current average cost. */
	SALE,
	/** Transfer dispatch from the origin branch — outbound, valued at the branch's average cost. */
	TRANSFER_OUT,
	/** Transfer receipt at the destination branch — inbound, valued at a supplied cost (P-03). */
	TRANSFER_IN,
	/** Manual adjustment increasing the balance (CU-INV-05, R-06) — inbound, valued at a supplied cost. */
	ADJUSTMENT_POS,
	/** Manual adjustment decreasing the balance (CU-INV-05, R-06) — outbound, valued at the average cost. */
	ADJUSTMENT_NEG,
	/** Damage, waste or expiry write-off (CU-INV-06) — outbound, valued at the average cost (R-12). */
	DAMAGE_WASTE,
	/** Initial stock load — inbound, valued at a supplied cost. */
	INITIAL_LOAD;

	/** {@code true} for the four types that add to {@code current_stock}. */
	public boolean isInbound() {
		return switch (this) {
			case PURCHASE_RECEIPT, TRANSFER_IN, ADJUSTMENT_POS, INITIAL_LOAD -> true;
			case SALE, TRANSFER_OUT, ADJUSTMENT_NEG, DAMAGE_WASTE -> false;
		};
	}

	/**
	 * {@code true} when the caller MUST supply a unit cost (P-03): the four inbound types.
	 * Outbound types MUST NOT carry a supplied cost — {@code inventory} stamps the branch's
	 * current average cost instead (RN-03). Expressed as its own method, not an alias of
	 * {@link #isInbound()}, so the intent reads at the call site.
	 */
	public boolean requiresSuppliedCost() {
		return isInbound();
	}
}
