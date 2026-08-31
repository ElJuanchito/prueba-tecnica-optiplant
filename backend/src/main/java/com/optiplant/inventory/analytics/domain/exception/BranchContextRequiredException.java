package com.optiplant.inventory.analytics.domain.exception;

/**
 * Thrown when a corporate {@code ADMIN} ({@code branchId == null}) requests a branch dashboard
 * without specifying {@code branchExternalId} (contract §7, R-02, PA-01).
 * Maps to {@code 403 branch_context_required}.
 */
public class BranchContextRequiredException extends RuntimeException {

	public BranchContextRequiredException() {
		super("A branch context is required for this operation. For cross-branch views, refer to /api/analytics/corporate/branches");
	}

	public BranchContextRequiredException(String message) {
		super(message);
	}
}
