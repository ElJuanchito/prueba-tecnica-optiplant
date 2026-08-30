package com.optiplant.inventory.purchases.domain.exception;

/**
 * A transition, edit or reception from a state that forbids it (R-10, R-11, R-14, RN-15). Maps to
 * {@code 409 invalid_order_state}. Raised by {@code PurchaseOrderStateMachine}.
 */
public class InvalidOrderStateException extends RuntimeException {

	public InvalidOrderStateException(String message) {
		super(message);
	}
}
