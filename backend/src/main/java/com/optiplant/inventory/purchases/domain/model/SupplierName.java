package com.optiplant.inventory.purchases.domain.model;

/**
 * A supplier's commercial name. Non-blank, at most 150 characters —
 * enforces {@code suppliers.name VARCHAR(150) NOT NULL} (R-01).
 */
public record SupplierName(String value) {

	private static final int MAX_LENGTH = 150;

	public SupplierName {
		if (value == null) {
			throw new IllegalArgumentException("supplier name must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("supplier name must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("supplier name must not exceed " + MAX_LENGTH + " characters");
		}
	}

	public static SupplierName of(String value) {
		return new SupplierName(value);
	}
}
