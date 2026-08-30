package com.optiplant.inventory.pricing.domain.exception;

import java.util.UUID;

/**
 * Thrown when a product {@code external_id} referenced in pricing operations names no product,
 * or a disabled one.
 * Maps to {@code 404 product_not_found}.
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID externalId) {
		super("Product not found for external id: " + externalId);
	}

	public ProductNotFoundException(String message) {
		super(message);
	}
}
