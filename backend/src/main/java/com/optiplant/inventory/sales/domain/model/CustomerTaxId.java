package com.optiplant.inventory.sales.domain.model;

/**
 * Customer tax identification on a sale (R-01, DT-04, design §4).
 * Enforces {@code sales.customer_tax_id VARCHAR(30)}.
 */
public record CustomerTaxId(String value) {

	private static final int MAX_LENGTH = 30;

	public CustomerTaxId {
		if (value != null) {
			value = value.strip();
			if (value.isEmpty()) {
				value = null;
			} else if (value.length() > MAX_LENGTH) {
				throw new IllegalArgumentException("customerTaxId must not exceed " + MAX_LENGTH + " characters");
			}
		}
	}

	public static CustomerTaxId of(String value) {
		return new CustomerTaxId(value);
	}
}
