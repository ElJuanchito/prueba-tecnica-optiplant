package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown by {@code AdjustmentPolicy} when the counted physical quantity equals the current
 * balance (R-08): quantity {@code 0} would violate {@code CHECK (quantity > 0)}, and a no-op is
 * not an audit event. The web layer maps this to {@code 400 adjustment_without_difference}.
 */
public class AdjustmentWithoutDifferenceException extends RuntimeException {

	public AdjustmentWithoutDifferenceException() {
		super("counted quantity equals the current balance; there is nothing to adjust");
	}
}
