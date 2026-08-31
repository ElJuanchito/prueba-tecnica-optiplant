package com.optiplant.inventory.purchases.domain.exception;

/**
 * A corporate {@code ADMIN} ({@code branchId == null}) creating an order or receiving goods — no
 * branch to derive (§5, R-07). Maps to {@code 403 branch_context_required}.
 */
public class BranchContextRequiredException extends RuntimeException {

	public BranchContextRequiredException() {
		super("A branch context is required for this operation");
	}

	public BranchContextRequiredException(String message) {
		super(message);
	}
}
