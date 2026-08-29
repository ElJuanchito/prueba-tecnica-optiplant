package com.optiplant.inventory.transfers.domain.exception;

import java.util.UUID;

/**
 * Thrown when a product {@code external_id} referenced by a transfer item names no product in
 * {@code catalog}, or a disabled one (R-03). {@code transfers}' own copy — {@code catalog}'s
 * cannot be imported across the module boundary. The web layer maps this to
 * {@code 404 product_not_found}.
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID externalId) {
		super("No active product found for external id " + externalId);
	}
}
