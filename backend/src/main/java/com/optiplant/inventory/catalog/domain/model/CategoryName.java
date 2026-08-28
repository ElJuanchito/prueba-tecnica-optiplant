package com.optiplant.inventory.catalog.domain.model;

import java.util.Locale;

/**
 * A category's display name (R-02). Normalization and every length/blank
 * invariant live here, once, so no use case can reach the persistence layer with
 * an unnormalized value — the same "validate in the value object, not in a DTO
 * annotation" rule the design fixes for {@code Sku} and {@code UnitCode}.
 *
 * <p>The surrounding whitespace is stripped and the resulting value must be
 * {@code 1..100} characters ({@code categories.name VARCHAR(100) NOT NULL},
 * {@code 01-init-schema.sql:81}). Case is <strong>preserved</strong> in
 * {@link #value()} because these are human-facing names; {@link #comparisonKey()}
 * exposes the lower-cased form used for the case-insensitive uniqueness of R-02
 * (the schema half is {@code uq_categories_name_ci}, S-4).
 *
 * <p>Violations throw {@link IllegalArgumentException}, which the web layer maps
 * to {@code 400 invalid_request}.
 */
public record CategoryName(String value) {

	private static final int MAX_LENGTH = 100;

	public CategoryName {
		if (value == null) {
			throw new IllegalArgumentException("category name must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("category name must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("category name must not exceed " + MAX_LENGTH + " characters");
		}
	}

	/** The lower-cased form for R-02's case-insensitive uniqueness check. */
	public String comparisonKey() {
		return value.toLowerCase(Locale.ROOT);
	}
}
