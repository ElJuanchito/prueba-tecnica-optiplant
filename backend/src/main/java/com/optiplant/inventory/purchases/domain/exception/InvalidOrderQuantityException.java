package com.optiplant.inventory.purchases.domain.exception;

/**
 * {@code ordered_quantity <= 0}, or a negative received quantity (R-05, R-16). Maps to
 * {@code 400 invalid_order_quantity}.
 */
public class InvalidOrderQuantityException extends RuntimeException {

	public InvalidOrderQuantityException(String message) {
		super(message);
	}
}
