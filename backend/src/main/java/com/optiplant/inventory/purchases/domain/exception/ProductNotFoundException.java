package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * A product {@code external_id} names nothing, or is disabled (R-08). Maps to
 * {@code 404 product_not_found}. Each module declares its own — the precedent {@code inventory},
 * {@code transfers} and {@code sales} all follow (design §3.5).
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID productExternalId) {
		super("Product not found or disabled: " + productExternalId);
	}

	public ProductNotFoundException(String message) {
		super(message);
	}
}
