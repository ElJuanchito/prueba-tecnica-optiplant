package com.optiplant.inventory.transfers.domain.exception;

import java.util.UUID;

/**
 * Thrown by {@link com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy} when a
 * transfer does not exist, or exists but involves neither of the caller's branches (RNF-SEC-03,
 * contract §5) — existence must not leak, so this is {@code 404}, never {@code 403}. The web
 * layer maps this to {@code 404 transfer_not_found}.
 */
public class TransferNotFoundException extends RuntimeException {

	public TransferNotFoundException(UUID externalId) {
		super("No transfer found for external id " + externalId);
	}
}
