package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown by {@code BranchScopePolicy} when a corporate {@code ADMIN} (whose
 * {@code AuthenticatedPrincipal.branchId()} is {@code null}) invokes a session-scoped mutation
 * (contract §5, PA-02). RN-14 forbids the branch ever arriving as a client parameter, so there is
 * no branch this operation can resolve to. The web layer maps this to
 * {@code 403 branch_context_required}.
 */
public class BranchContextRequiredException extends RuntimeException {

	public BranchContextRequiredException() {
		super("a corporate ADMIN has no branch context for this operation");
	}
}
