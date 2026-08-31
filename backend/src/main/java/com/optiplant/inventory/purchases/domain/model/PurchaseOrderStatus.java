package com.optiplant.inventory.purchases.domain.model;

/**
 * The five {@code purchase_orders.status} literals ({@code 01-init-schema.sql:270-272}).
 * {@code PurchaseOrderStateMachine} is the only authority on which transitions connect them
 * (R-11). {@code RECEIVED} and {@code CANCELLED} are terminal.
 */
public enum PurchaseOrderStatus {

	PENDING,
	APPROVED,
	PARTIALLY_RECEIVED,
	RECEIVED,
	CANCELLED;

	/** {@code true} for the two states that accept no further transition (R-11). */
	public boolean isTerminal() {
		return this == RECEIVED || this == CANCELLED;
	}
}
