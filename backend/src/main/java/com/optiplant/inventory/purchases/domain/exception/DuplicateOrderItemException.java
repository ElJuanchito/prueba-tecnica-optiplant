package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * The same product appears twice in one order (R-08). Maps to {@code 400 duplicate_order_item}.
 */
public class DuplicateOrderItemException extends RuntimeException {

	public DuplicateOrderItemException(UUID productExternalId) {
		super("Duplicate product in purchase order items: " + productExternalId);
	}

	public DuplicateOrderItemException(String message) {
		super(message);
	}
}
