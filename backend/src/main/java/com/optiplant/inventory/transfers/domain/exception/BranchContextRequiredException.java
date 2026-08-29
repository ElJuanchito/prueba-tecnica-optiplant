package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown when a corporate {@code ADMIN} (whose {@code AuthenticatedPrincipal.branchId()} is
 * {@code null}) requests a transfer (R-05): there is no destination branch to derive from the
 * session, and RN-14 forbids it arriving as a client parameter. The web layer maps this to
 * {@code 403 branch_context_required}.
 */
public class BranchContextRequiredException extends RuntimeException {

	public BranchContextRequiredException() {
		super("a corporate ADMIN has no branch context to derive a transfer destination from");
	}
}
