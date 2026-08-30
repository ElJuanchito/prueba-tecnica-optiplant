package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown when the same product appears twice in a transfer request (R-03), or the same item
 * {@code external_id} appears twice in an approval, dispatch or receipt payload. The web layer
 * maps this to {@code 400 duplicate_transfer_item}.
 */
public class DuplicateTransferItemException extends RuntimeException {

	public DuplicateTransferItemException() {
		super("the same product or item must not appear twice");
	}
}
