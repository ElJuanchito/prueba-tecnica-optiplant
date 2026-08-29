package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive route duration in hours, scale 2 {@code HALF_UP}, matching
 * {@code logistics_routes.estimated_duration_hours NUMERIC(6,2)} and its
 * {@code CHECK (estimated_duration_hours > 0)} (design §4).
 */
public record RouteDuration(BigDecimal value) {

	private static final int SCALE = 2;

	public RouteDuration {
		if (value == null) {
			throw new IllegalArgumentException("estimated duration must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new IllegalArgumentException("estimated duration must be strictly positive");
		}
	}
}
