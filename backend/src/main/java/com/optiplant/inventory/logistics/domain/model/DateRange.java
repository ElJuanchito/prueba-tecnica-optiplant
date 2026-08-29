package com.optiplant.inventory.logistics.domain.model;

import java.time.Instant;

/**
 * The compliance report's mandatory date range (contract §6 {@code from}/{@code to}). Both
 * bounds are required here — unlike {@code inventory}'s optional-range Kardex query — since
 * RF-LOG-02/RF-LOG-04 always report over a bounded period.
 */
public record DateRange(Instant from, Instant to) {

	public DateRange {
		if (from == null || to == null) {
			throw new IllegalArgumentException("both 'from' and 'to' are required");
		}
		if (from.isAfter(to)) {
			throw new IllegalArgumentException("date range 'from' must not be after 'to'");
		}
	}
}
