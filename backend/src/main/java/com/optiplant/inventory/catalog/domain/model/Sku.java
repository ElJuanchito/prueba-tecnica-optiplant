package com.optiplant.inventory.catalog.domain.model;

import java.util.Locale;

/**
 * A product's stock-keeping unit (R-06). Normalization and every length/blank
 * invariant live here, once — the same "validate in the value object, not in a
 * DTO annotation" rule {@code CategoryName} already follows.
 *
 * <p>The surrounding whitespace is stripped <strong>and the value is
 * upper-cased</strong>, then it must be {@code 1..50} characters
 * ({@code products.sku VARCHAR(50) NOT NULL UNIQUE}, {@code 01-init-schema.sql:92}).
 * Because every persisted SKU is upper-case, that existing {@code UNIQUE} index is
 * a sufficient guarantee of R-06: {@code abc-1} and {@code ABC-1} cannot coexist
 * as two different articles.
 *
 * <p>Violations throw {@link IllegalArgumentException}, which the web layer maps
 * to {@code 400 invalid_request}.
 */
public record Sku(String value) {

	private static final int MAX_LENGTH = 50;

	public Sku {
		if (value == null) {
			throw new IllegalArgumentException("sku must not be null");
		}
		value = value.strip().toUpperCase(Locale.ROOT);
		if (value.isEmpty()) {
			throw new IllegalArgumentException("sku must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("sku must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
