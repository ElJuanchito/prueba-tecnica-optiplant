package com.optiplant.inventory.inventory.domain.model;

import java.time.Instant;

/**
 * An optional Kardex query date range (R-16). Both bounds are nullable — an absent bound means
 * "unbounded" on that side. When both are present, {@code from} MUST NOT be after {@code to}.
 *
 * <p>Violations throw {@link IllegalArgumentException}, mapped to {@code 400 invalid_request}
 * ("malformed date range").
 */
public record DateRange(Instant from, Instant to) {

	public DateRange {
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("date range 'from' must not be after 'to'");
		}
	}
}
