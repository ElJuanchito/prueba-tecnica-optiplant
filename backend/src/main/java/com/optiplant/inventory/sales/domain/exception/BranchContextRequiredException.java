package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when a corporate ADMIN without a branch context attempts a session-scoped operation (R-02).
 * Maps to {@code 403 branch_context_required}.
 */
public class BranchContextRequiredException extends RuntimeException {

	public BranchContextRequiredException() {
		super("Operation requires an active branch context in the authenticated session");
	}

	public BranchContextRequiredException(String message) {
		super(message);
	}
}
