package com.optiplant.inventory.inventory.domain.exception;

import java.util.UUID;

/**
 * Thrown when a product {@code external_id} referenced by an {@code inventory} operation names
 * no product in {@code catalog}. {@code inventory}'s own exception — {@code catalog}'s cannot be
 * imported across the module boundary. The web layer maps this to {@code 404 product_not_found}.
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID externalId) {
		super("No product found for external id " + externalId);
	}
}
