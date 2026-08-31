package com.optiplant.inventory.analytics.domain.exception;

import java.util.UUID;

/**
 * Thrown when a queried {@code productExternalId} is not found (contract §7, R-24, D-2).
 * Maps to {@code 404 product_not_found}.
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID externalId) {
		super("No product found for external id " + externalId);
	}
}
