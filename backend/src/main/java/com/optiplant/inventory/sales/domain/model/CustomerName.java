package com.optiplant.inventory.sales.domain.model;

/**
 * Customer name on a sale (R-01, design §4).
 * Enforces {@code sales.customer_name VARCHAR(150) NOT NULL}.
 */
public record CustomerName(String value) {

	private static final int MAX_LENGTH = 150;

	public CustomerName {
		if (value == null) {
			throw new IllegalArgumentException("customerName must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("customerName must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("customerName must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
