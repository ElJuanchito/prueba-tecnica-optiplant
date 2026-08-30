package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when an authenticated actor attempts an unauthorized operation on a sale of another branch (R-22).
 * Maps to {@code 403 cross_branch_access_denied}.
 */
public class CrossBranchAccessDeniedException extends RuntimeException {

	public CrossBranchAccessDeniedException() {
		super("Access denied: cannot mutate a sale from another branch");
	}

	public CrossBranchAccessDeniedException(String message) {
		super(message);
	}
}
