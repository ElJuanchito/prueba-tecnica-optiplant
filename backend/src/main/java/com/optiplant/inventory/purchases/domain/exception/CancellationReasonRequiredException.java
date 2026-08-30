package com.optiplant.inventory.purchases.domain.exception;

/**
 * A blank cancellation reason (R-13, F-3). Maps to {@code 400 cancellation_reason_required}.
 */
public class CancellationReasonRequiredException extends RuntimeException {

	public CancellationReasonRequiredException() {
		super("A non-blank cancellation reason is required");
	}

	public CancellationReasonRequiredException(String message) {
		super(message);
	}
}
