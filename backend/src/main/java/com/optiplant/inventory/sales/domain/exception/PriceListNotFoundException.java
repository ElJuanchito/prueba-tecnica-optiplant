package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when a price list {@code external_id} names no price list, or an inactive one (R-10).
 * Maps to {@code 404 price_list_not_found}.
 */
public class PriceListNotFoundException extends RuntimeException {

	public PriceListNotFoundException(UUID externalId) {
		super("Price list not found for external id: " + externalId);
	}

	public PriceListNotFoundException(String message) {
		super(message);
	}
}
