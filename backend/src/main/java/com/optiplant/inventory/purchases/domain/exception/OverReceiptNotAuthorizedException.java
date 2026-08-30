package com.optiplant.inventory.purchases.domain.exception;

/**
 * An {@code OPERATOR} receiving above a line's pending balance (R-16, PA-02). Maps to
 * {@code 403 over_receipt_requires_manager}. Accepted for {@code BRANCH_MANAGER} / {@code ADMIN},
 * with the excess recorded in the audit entry.
 */
public class OverReceiptNotAuthorizedException extends RuntimeException {

	public OverReceiptNotAuthorizedException(String message) {
		super(message);
	}
}
