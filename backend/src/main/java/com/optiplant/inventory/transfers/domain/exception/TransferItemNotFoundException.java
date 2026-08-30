package com.optiplant.inventory.transfers.domain.exception;

import java.util.UUID;

/**
 * Thrown when an item {@code external_id} referenced in an approval, dispatch or receipt payload
 * does not belong to the transfer being mutated. The web layer maps this to
 * {@code 404 transfer_item_not_found}.
 */
public class TransferItemNotFoundException extends RuntimeException {

	public TransferItemNotFoundException(UUID externalId) {
		super("No transfer item found for external id " + externalId);
	}
}
