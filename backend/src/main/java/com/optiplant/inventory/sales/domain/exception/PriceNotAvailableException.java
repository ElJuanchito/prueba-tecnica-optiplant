package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when no eligible price is available for a product under the applied price list (R-11, RN-16).
 * Maps to {@code 409 price_not_available}.
 */
public class PriceNotAvailableException extends RuntimeException {

	public PriceNotAvailableException(UUID productExternalId) {
		super("Price not available for product: " + productExternalId);
	}

	public PriceNotAvailableException(String message) {
		super(message);
	}
}
