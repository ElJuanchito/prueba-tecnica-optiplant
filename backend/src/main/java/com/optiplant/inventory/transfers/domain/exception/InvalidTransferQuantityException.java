package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown when a quantity violates its transition's bound (R-07: approved above requested; R-13:
 * dispatched above the agreed quantity; R-19: received above dispatched or negative), or when an
 * approval/dispatch/receipt payload does not name every item of the transfer exactly once. The
 * web layer maps this to {@code 400 invalid_transfer_quantity}.
 */
public class InvalidTransferQuantityException extends RuntimeException {

	public InvalidTransferQuantityException(String message) {
		super(message);
	}
}
