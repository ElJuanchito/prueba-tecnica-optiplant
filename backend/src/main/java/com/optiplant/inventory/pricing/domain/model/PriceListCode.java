package com.optiplant.inventory.pricing.domain.model;

import java.util.Locale;

/**
 * A price list's unique commercial code (e.g. {@code "RETAIL"}, {@code "WHOLESALE"}).
 * Enforces {@code price_lists.code VARCHAR(30) UNIQUE}.
 */
public record PriceListCode(String value) {

	private static final int MAX_LENGTH = 30;

	public PriceListCode {
		if (value == null) {
			throw new IllegalArgumentException("code must not be null");
		}
		value = value.strip().toUpperCase(Locale.ROOT);
		if (value.isEmpty()) {
			throw new IllegalArgumentException("code must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("code must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
