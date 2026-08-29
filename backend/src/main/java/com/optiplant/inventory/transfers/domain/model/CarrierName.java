package com.optiplant.inventory.transfers.domain.model;

/**
 * The carrier recorded at dispatch (R-10, design §3.1), trimmed and bounded to
 * {@code carrier_name VARCHAR(100)}.
 */
public record CarrierName(String value) {

	private static final int MAX_LENGTH = 100;

	public CarrierName {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("carrier name must not be blank");
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("carrier name must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
