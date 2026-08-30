package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when the same product appears multiple times in a sale basket (R-06).
 * Maps to {@code 400 duplicate_sale_item}.
 */
public class DuplicateSaleItemException extends RuntimeException {

	public DuplicateSaleItemException(UUID productExternalId) {
		super("Duplicate product in sale items: " + productExternalId);
	}

	public DuplicateSaleItemException(String message) {
		super(message);
	}
}
