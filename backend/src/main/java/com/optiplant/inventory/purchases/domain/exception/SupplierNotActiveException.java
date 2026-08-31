package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * A disabled supplier named on a new order (R-04). Maps to {@code 409 supplier_not_active}.
 */
public class SupplierNotActiveException extends RuntimeException {

	public SupplierNotActiveException(UUID externalId) {
		super("Supplier is not active: " + externalId);
	}

	public SupplierNotActiveException(String message) {
		super(message);
	}
}
