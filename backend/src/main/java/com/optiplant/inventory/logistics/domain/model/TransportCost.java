package com.optiplant.inventory.logistics.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative transport cost, scale 2 {@code HALF_UP}, matching
 * {@code logistics_routes.transport_cost NUMERIC(12,2)} and its {@code CHECK (transport_cost >= 0)}
 * (design §4).
 */
public record TransportCost(BigDecimal value) {

	private static final int SCALE = 2;

	public TransportCost {
		if (value == null) {
			throw new IllegalArgumentException("transport cost must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new IllegalArgumentException("transport cost must not be negative");
		}
	}
}
