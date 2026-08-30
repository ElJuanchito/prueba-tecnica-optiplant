package com.optiplant.inventory.sales.domain.model;

/**
 * Lifecycle status of a sale (design §4).
 * Matches {@code sales.status VARCHAR(20)} check constraint literals.
 */
public enum SaleStatus {

	COMPLETED,
	CANCELLED;

	/**
	 * Returns {@code true} if a sale in this state can be voided/cancelled (R-18).
	 */
	public boolean isCancellable() {
		return this == COMPLETED;
	}
}
