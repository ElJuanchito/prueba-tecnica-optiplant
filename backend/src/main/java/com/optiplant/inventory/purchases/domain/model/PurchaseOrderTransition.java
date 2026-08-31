package com.optiplant.inventory.purchases.domain.model;

/**
 * The four transitions {@code PurchaseOrderStateMachine} arbitrates (D-3): {@code EDIT} puts
 * R-10's refusal in the same table as R-11's; whether a {@code RECEIVE} lands on
 * {@code PARTIALLY_RECEIVED} or {@code RECEIVED} is decided by {@code PurchaseReceptionPolicy}
 * after the plan is computed, so it is not split.
 */
public enum PurchaseOrderTransition {
	EDIT, APPROVE, CANCEL, RECEIVE
}
