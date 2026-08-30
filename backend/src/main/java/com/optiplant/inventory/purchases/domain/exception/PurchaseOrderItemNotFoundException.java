package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * A reception line names an item outside this order (design §5 step 4). Maps to
 * {@code 404 purchase_order_item_not_found}.
 */
public class PurchaseOrderItemNotFoundException extends RuntimeException {

	public PurchaseOrderItemNotFoundException(UUID itemExternalId) {
		super("Purchase order item not found for external id: " + itemExternalId);
	}

	public PurchaseOrderItemNotFoundException(String message) {
		super(message);
	}
}
