package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when a customer is not found (R-C3, R-C8, R-C14, contract §8).
 * Maps to {@code 404 customer_not_found}.
 */
public class CustomerNotFoundException extends RuntimeException {

	public CustomerNotFoundException(UUID externalId) {
		super("Customer not found for external id: " + externalId);
	}

	public CustomerNotFoundException(String message) {
		super(message);
	}
}
