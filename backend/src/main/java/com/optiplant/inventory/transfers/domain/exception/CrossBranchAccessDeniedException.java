package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown by {@link com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy} when
 * the actor's branch is visible on the transfer (origin or destination) but is not the side the
 * transition requires (R-06, R-10, R-15, R-21). {@code transfers} declares its own copy rather
 * than importing {@code inventory}'s — boundary rule 3 forbids the import. The web layer maps
 * this to {@code 403 cross_branch_access_denied}.
 */
public class CrossBranchAccessDeniedException extends RuntimeException {

	public CrossBranchAccessDeniedException() {
		super("the caller's branch is not the side this operation requires");
	}
}
