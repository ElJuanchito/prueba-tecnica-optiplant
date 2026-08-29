package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown when an outbound movement would drive {@code current_stock} below zero (R-11, RN-01).
 * Raised by {@code StockMutationPolicy} before any write — {@code current_stock >= 0} is the
 * schema's last line of defence (T-07), never the first. The web layer maps this to
 * {@code 409 insufficient_stock}.
 */
public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(String message) {
		super(message);
	}
}
