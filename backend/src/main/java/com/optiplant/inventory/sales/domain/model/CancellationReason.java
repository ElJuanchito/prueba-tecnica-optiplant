package com.optiplant.inventory.sales.domain.model;

import com.optiplant.inventory.sales.domain.exception.SaleReasonRequiredException;

/**
 * Mandatory reason for voiding a sale (R-18, design §4).
 * Enforces non-blank, length &lt;= 480.
 */
public record CancellationReason(String value) {

	private static final int MAX_LENGTH = 480;

	public CancellationReason {
		if (value == null || value.isBlank()) {
			throw new SaleReasonRequiredException();
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("reason must not exceed " + MAX_LENGTH + " characters");
		}
	}

	public static CancellationReason of(String value) {
		return new CancellationReason(value);
	}
}
