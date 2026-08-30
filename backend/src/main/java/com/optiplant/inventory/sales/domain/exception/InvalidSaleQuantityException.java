package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when a sale quantity is invalid (less than or equal to zero) (R-01).
 * Maps to {@code 400 invalid_sale_quantity}.
 */
public class InvalidSaleQuantityException extends RuntimeException {

	public InvalidSaleQuantityException(String message) {
		super(message);
	}
}
