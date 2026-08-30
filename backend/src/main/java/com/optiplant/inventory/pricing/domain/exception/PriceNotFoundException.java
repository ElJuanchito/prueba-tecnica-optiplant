package com.optiplant.inventory.pricing.domain.exception;

import java.util.UUID;

/**
 * Thrown when a price {@code external_id} names no price row on closure (contract §7).
 * Maps to {@code 404 price_not_found}.
 */
public class PriceNotFoundException extends RuntimeException {

	public PriceNotFoundException(UUID externalId) {
		super("Price not found for external id: " + externalId);
	}

	public PriceNotFoundException(String message) {
		super(message);
	}
}
