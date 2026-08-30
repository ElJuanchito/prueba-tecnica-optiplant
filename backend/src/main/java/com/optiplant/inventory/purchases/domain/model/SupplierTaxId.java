package com.optiplant.inventory.purchases.domain.model;

/**
 * A supplier's tax identification (NIT / RUC). Non-blank, at most 30 characters —
 * enforces {@code suppliers.tax_id VARCHAR(30) NOT NULL UNIQUE} at the domain boundary
 * before any SQL (R-01, T-07).
 */
public record SupplierTaxId(String value) {

	private static final int MAX_LENGTH = 30;

	public SupplierTaxId {
		if (value == null) {
			throw new IllegalArgumentException("supplier tax id must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("supplier tax id must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("supplier tax id must not exceed " + MAX_LENGTH + " characters");
		}
	}

	public static SupplierTaxId of(String value) {
		return new SupplierTaxId(value);
	}
}
