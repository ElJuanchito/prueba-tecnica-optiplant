package com.optiplant.inventory.transfers.domain.model;

/**
 * The six {@code transfers.status} literals ({@code 01-init-schema.sql:371-380}), design §3.2.
 * {@link TransferStateMachine} is the only authority on which transitions connect them (R-01).
 */
public enum TransferStatus {

	/** 1. Requested by the destination branch. */
	REQUESTED,
	/** 2. Approved / being prepared at the origin. */
	IN_PREPARATION,
	/** 3. Dispatched — stock discounted to "in transit". */
	IN_TRANSIT,
	/** 4. Received in full, no discrepancy. */
	RECEIVED,
	/** 5. Received with a shortfall (R-18). */
	RECEIVED_WITH_DISCREPANCY,
	/** 6. Cancelled before dispatch. */
	CANCELLED;

	/** {@code true} for the three states R-25's monitor filters on. */
	public boolean isActive() {
		return this == REQUESTED || this == IN_PREPARATION || this == IN_TRANSIT;
	}

	/** {@code true} for the three states that accept no further transition (R-01). */
	public boolean isTerminal() {
		return !isActive();
	}
}
