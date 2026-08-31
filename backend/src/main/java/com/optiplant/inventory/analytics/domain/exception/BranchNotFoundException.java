package com.optiplant.inventory.analytics.domain.exception;

import java.util.UUID;

/**
 * Thrown when an {@code ADMIN} caller specifies a {@code branchExternalId} that does not exist
 * or is inactive (contract §7, R-02).
 * Maps to {@code 404 branch_not_found}.
 */
public class BranchNotFoundException extends RuntimeException {

	public BranchNotFoundException(UUID externalId) {
		super("No active branch found for external id " + externalId);
	}
}
