package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown by {@link com.optiplant.inventory.transfers.domain.model.TransferReason} on a blank or
 * absent reason, and directly by the receipt policy when a shortfall carries none (R-09, R-18,
 * R-21). The web layer maps this to {@code 400 transfer_reason_required}.
 */
public class TransferReasonRequiredException extends RuntimeException {

	public TransferReasonRequiredException() {
		super("a non-blank reason is required for this operation");
	}
}
