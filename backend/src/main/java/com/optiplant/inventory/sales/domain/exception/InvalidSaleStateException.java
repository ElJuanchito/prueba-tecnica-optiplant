package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when attempting an operation not permitted by the sale's current lifecycle state (R-18).
 * Maps to {@code 409 invalid_sale_state}.
 */
public class InvalidSaleStateException extends RuntimeException {

	public InvalidSaleStateException(String message) {
		super(message);
	}
}
