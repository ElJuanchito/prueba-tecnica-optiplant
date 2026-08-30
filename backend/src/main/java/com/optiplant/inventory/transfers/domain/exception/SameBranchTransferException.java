package com.optiplant.inventory.transfers.domain.exception;

/**
 * Thrown when a transfer request names the same branch as origin and destination (R-03). The web
 * layer maps this to {@code 400 same_branch_transfer}.
 */
public class SameBranchTransferException extends RuntimeException {

	public SameBranchTransferException() {
		super("origin and destination branch must differ");
	}
}
