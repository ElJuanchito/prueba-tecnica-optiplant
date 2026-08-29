package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown by {@code BranchScopePolicy} as defence in depth (RN-14) when a mutation resolves to a
 * branch other than the caller's own — a state the current API surface cannot reach (no endpoint
 * accepts a branch parameter), guarded anyway in case a future caller mis-derives one. The web
 * layer maps this to {@code 403 cross_branch_access_denied}.
 */
public class CrossBranchAccessDeniedException extends RuntimeException {

	public CrossBranchAccessDeniedException() {
		super("the resolved branch does not match the caller's own branch");
	}
}
