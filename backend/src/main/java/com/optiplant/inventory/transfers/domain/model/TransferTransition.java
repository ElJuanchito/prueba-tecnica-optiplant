package com.optiplant.inventory.transfers.domain.model;

/** The five transitions {@link TransferStateMachine} arbitrates (R-01, design §3.3). */
public enum TransferTransition {
	APPROVE, REJECT, DISPATCH, RECEIVE, CANCEL
}
