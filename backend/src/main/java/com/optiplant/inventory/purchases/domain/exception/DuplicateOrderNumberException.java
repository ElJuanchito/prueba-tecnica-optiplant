package com.optiplant.inventory.purchases.domain.exception;

/**
 * {@code purchase_orders.order_number} {@code UNIQUE} violated despite the advisory lock — the
 * last line of defence (F-9, T-07). Maps to {@code 409 duplicate_order_number}.
 */
public class DuplicateOrderNumberException extends RuntimeException {

	public DuplicateOrderNumberException() {
		super("A purchase order with this number already exists");
	}

	public DuplicateOrderNumberException(String message) {
		super(message);
	}
}
