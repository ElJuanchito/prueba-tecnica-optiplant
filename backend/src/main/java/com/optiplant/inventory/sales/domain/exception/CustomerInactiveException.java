package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when associating a deactivated customer with a new sale (R-C7, D-4, contract §8).
 * Maps to {@code 409 customer_inactive}.
 */
public class CustomerInactiveException extends RuntimeException {

	public CustomerInactiveException(UUID externalId) {
		super("Customer is inactive and cannot be associated with a new sale: " + externalId);
	}

	public CustomerInactiveException(String message) {
		super(message);
	}
}
