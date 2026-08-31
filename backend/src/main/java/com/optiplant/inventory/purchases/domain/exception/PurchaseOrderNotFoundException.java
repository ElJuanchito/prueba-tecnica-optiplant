package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * An unknown order, <strong>or</strong> one belonging to another branch (R-23, R-25). Maps to
 * {@code 404 purchase_order_not_found} — never {@code 403}, which would confirm it exists.
 */
public class PurchaseOrderNotFoundException extends RuntimeException {

	public PurchaseOrderNotFoundException(UUID externalId) {
		super("Purchase order not found for external id: " + externalId);
	}

	public PurchaseOrderNotFoundException(String message) {
		super(message);
	}
}
