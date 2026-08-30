package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when a sale is not found, or belongs to another branch (R-25).
 * Maps to {@code 404 sale_not_found}.
 */
public class SaleNotFoundException extends RuntimeException {

	public SaleNotFoundException(UUID externalId) {
		super("Sale not found for external id: " + externalId);
	}

	public SaleNotFoundException(String message) {
		super(message);
	}
}
