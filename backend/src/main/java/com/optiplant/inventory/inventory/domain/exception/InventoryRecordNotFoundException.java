package com.optiplant.inventory.inventory.domain.exception;

import java.util.UUID;

/**
 * Thrown when no {@code branch_inventories} row exists for a branch/product pair and the
 * operation cannot create one on demand (F-3). The web layer maps this to
 * {@code 404 inventory_record_not_found}.
 */
public class InventoryRecordNotFoundException extends RuntimeException {

	public InventoryRecordNotFoundException(UUID branchExternalId, UUID productExternalId) {
		super("No branch inventory record found for branch " + branchExternalId + " and product " + productExternalId);
	}
}
