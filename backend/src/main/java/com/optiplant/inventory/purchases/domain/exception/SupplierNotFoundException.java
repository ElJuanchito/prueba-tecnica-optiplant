package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * An unknown supplier {@code external_id}. Maps to {@code 404 supplier_not_found} (contract §7).
 */
public class SupplierNotFoundException extends RuntimeException {

	public SupplierNotFoundException(UUID externalId) {
		super("Supplier not found for external id: " + externalId);
	}

	public SupplierNotFoundException(String message) {
		super(message);
	}
}
