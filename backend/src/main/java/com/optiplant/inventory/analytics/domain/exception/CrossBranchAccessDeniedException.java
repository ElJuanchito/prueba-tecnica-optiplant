package com.optiplant.inventory.analytics.domain.exception;

/**
 * Thrown when a non-{@code ADMIN} caller ({@code BRANCH_MANAGER} or {@code OPERATOR}) supplies
 * {@code branchExternalId} (contract §7, R-02).
 * Maps to {@code 403 cross_branch_access_denied}.
 */
public class CrossBranchAccessDeniedException extends RuntimeException {

	public CrossBranchAccessDeniedException() {
		super("Cross-branch access is denied for this role");
	}

	public CrossBranchAccessDeniedException(String message) {
		super(message);
	}
}
