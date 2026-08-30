package com.optiplant.inventory.pricing.domain.exception;

import java.util.UUID;

/**
 * Thrown when a branch {@code external_id} referenced in pricing operations names no branch,
 * or an inactive one.
 * Maps to {@code 404 branch_not_found}.
 */
public class BranchNotFoundException extends RuntimeException {

	public BranchNotFoundException(UUID externalId) {
		super("Branch not found for external id: " + externalId);
	}

	public BranchNotFoundException(String message) {
		super(message);
	}
}
