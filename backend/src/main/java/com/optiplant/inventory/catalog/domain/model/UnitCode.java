package com.optiplant.inventory.catalog.domain.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A unit-of-measure code — a product's base unit (R-07) or an alternative unit's
 * {@code unit_name} (R-13). One type, one character set, two explicit bounds.
 *
 * <p>The value is stripped, upper-cased, must match {@code ^[A-Z0-9_]+$} and, for
 * the canonical constructor, be {@code 1..50} characters
 * ({@code product_units.unit_name VARCHAR(50)}, {@code 01-init-schema.sql:109}).
 * {@link #baseUnit(String)} applies the same rules with the tighter bound
 * {@code 1..20} ({@code products.base_unit VARCHAR(20)}, {@code :97}), so a base
 * unit is always assignable to a {@code unit_name} column but not the reverse.
 *
 * <p>Because the character set excludes whitespace, a value such as
 * {@code "Saco de 50"} is rejected: after normalization it is {@code "SACO DE 50"}
 * and the spaces are not in {@code ^[A-Z0-9_]+$}.
 *
 * <p>Violations throw {@link IllegalArgumentException}, which the web layer maps
 * to {@code 400 invalid_request}.
 */
public record UnitCode(String value) {

	private static final int MAX_LENGTH = 50;
	private static final int MAX_BASE_UNIT_LENGTH = 20;
	private static final Pattern FORMAT = Pattern.compile("^[A-Z0-9_]+$");

	public UnitCode {
		value = normalize(value, MAX_LENGTH);
	}

	/** Same normalization and format as the canonical constructor, bounded at {@code 1..20} (R-07). */
	public static UnitCode baseUnit(String raw) {
		return new UnitCode(normalize(raw, MAX_BASE_UNIT_LENGTH));
	}

	private static String normalize(String raw, int maxLength) {
		if (raw == null) {
			throw new IllegalArgumentException("unit code must not be null");
		}
		String normalized = raw.strip().toUpperCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("unit code must not be blank");
		}
		if (normalized.length() > maxLength) {
			throw new IllegalArgumentException("unit code must not exceed " + maxLength + " characters");
		}
		if (!FORMAT.matcher(normalized).matches()) {
			throw new IllegalArgumentException("unit code must match ^[A-Z0-9_]+$");
		}
		return normalized;
	}
}
