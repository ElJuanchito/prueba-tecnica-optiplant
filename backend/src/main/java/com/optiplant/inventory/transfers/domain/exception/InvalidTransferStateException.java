package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown by {@link com.optiplant.inventory.transfers.domain.service.TransferStateMachine} when a
 * transition's source state is not the one R-01 requires — including a dispatch attempted from
 * {@code REQUESTED} (R-14) or a cancellation attempted from {@code IN_TRANSIT} (R-22). The web
 * layer maps this to {@code 409 invalid_transfer_state}.
 */
public class InvalidTransferStateException extends RuntimeException {

	public InvalidTransferStateException(String message) {
		super(message);
	}
}
