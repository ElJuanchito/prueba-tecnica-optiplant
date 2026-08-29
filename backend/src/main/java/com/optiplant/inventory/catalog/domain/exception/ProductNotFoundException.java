package com.optiplant.inventory.catalog.domain.exception;

import java.util.UUID;

/**
 * Thrown when a product lookup by {@code external_id} names no product (R-09,
 * R-10, R-11). Mirrors {@code iam}'s {@code UserNotFoundException}; the web layer
 * maps it to {@code 404 product_not_found}. An inactive product is still found —
 * only a missing one raises this (R-10).
 */
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(UUID externalId) {
		super("No product found for external id " + externalId);
	}
}
