package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown by {@link com.optiplant.inventory.inventory.domain.model.MovementReason} when a manual
 * adjustment or write-off carries a blank or absent reason (RN-11, R-07). The web layer maps
 * this to {@code 400 adjustment_reason_required} — its own code, not the generic
 * {@code invalid_request}.
 */
public class AdjustmentReasonRequiredException extends RuntimeException {

	public AdjustmentReasonRequiredException() {
		super("a non-blank reason is required for this movement");
	}
}
