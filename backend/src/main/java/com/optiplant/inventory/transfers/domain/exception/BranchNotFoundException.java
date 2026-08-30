package com.optiplant.inventory.transfers.domain.exception;

import java.util.UUID;

/**
 * Thrown when a branch {@code external_id} referenced by a transfer request (the
 * {@code originBranchExternalId} reference, R-03) names no branch, or an inactive one.
 * {@code transfers}' own copy, mirroring {@code inventory}'s exception of the same name in a
 * different module. The web layer maps this to {@code 404 branch_not_found}.
 */
public class BranchNotFoundException extends RuntimeException {

	public BranchNotFoundException(UUID externalId) {
		super("No active branch found for external id " + externalId);
	}
}
