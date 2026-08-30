package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when attempting to void a sale without a mandatory reason (R-18).
 * Maps to {@code 400 sale_reason_required}.
 */
public class SaleReasonRequiredException extends RuntimeException {

	public SaleReasonRequiredException() {
		super("A cancellation reason is required to void a sale");
	}

	public SaleReasonRequiredException(String message) {
		super(message);
	}
}
