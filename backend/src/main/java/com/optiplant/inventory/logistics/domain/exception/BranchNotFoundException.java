package com.optiplant.inventory.logistics.domain.exception;

import java.util.UUID;

/**
 * Thrown when a branch {@code external_id} referenced by a route names no branch, or an
 * inactive one. {@code logistics}' own copy — see {@code RoutePriority}'s Javadoc. The web layer
 * maps this to {@code 404 branch_not_found}.
 */
public class BranchNotFoundException extends RuntimeException {

	public BranchNotFoundException(UUID externalId) {
		super("No active branch found for external id " + externalId);
	}
}
