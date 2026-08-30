package com.optiplant.inventory.pricing.domain.model;

import java.time.LocalDate;

/**
 * The period during which a price row is valid.
 * Enforces {@code check_price_period} (valid_to is null or valid_to >= valid_from).
 *
 * @param from inclusive start date
 * @param to   inclusive end date, or null if open-ended
 */
public record ValidityRange(LocalDate from, LocalDate to) {

	public ValidityRange {
		if (from == null) {
			throw new IllegalArgumentException("validFrom must not be null");
		}
		if (to != null && to.isBefore(from)) {
			throw new IllegalArgumentException("validTo (" + to + ") must not be before validFrom (" + from + ")");
		}
	}

	public static ValidityRange open(LocalDate from) {
		return new ValidityRange(from, null);
	}

	/**
	 * Checks whether the given date falls within this validity range (inclusive).
	 */
	public boolean coversAt(LocalDate date) {
		if (date == null) {
			return false;
		}
		boolean afterOrOnFrom = !date.isBefore(from);
		boolean beforeOrOnTo = (to == null) || !date.isAfter(to);
		return afterOrOnFrom && beforeOrOnTo;
	}
}
