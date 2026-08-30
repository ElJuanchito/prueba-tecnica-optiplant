package com.optiplant.inventory.pricing.domain.model;

/**
 * A price list's human-readable name.
 * Enforces {@code price_lists.name VARCHAR(100) NOT NULL}.
 */
public record PriceListName(String value) {

	private static final int MAX_LENGTH = 100;

	public PriceListName {
		if (value == null) {
			throw new IllegalArgumentException("name must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("name must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
