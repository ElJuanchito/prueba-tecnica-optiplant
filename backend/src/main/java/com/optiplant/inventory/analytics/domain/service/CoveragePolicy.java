package com.optiplant.inventory.analytics.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure function computing coverage days (contract R-10, R-15, design §4 D-7).
 * Three outcomes:
 * <ul>
 * <li>{@code 0.00} when stock is zero or negative.</li>
 * <li>{@code null} when demand is zero (never infinity or division by zero).</li>
 * <li>{@code currentStock ÷ (unitsSold ÷ periodDays)} at scale 2 otherwise.</li>
 * </ul>
 */
public final class CoveragePolicy {

	private CoveragePolicy() {
	}

	public static BigDecimal calculateCoverageDays(BigDecimal currentStock, BigDecimal unitsSold, int periodDays) {
		if (currentStock == null || currentStock.compareTo(BigDecimal.ZERO) <= 0) {
			return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
		}
		if (unitsSold == null || unitsSold.compareTo(BigDecimal.ZERO) <= 0 || periodDays <= 0) {
			return null;
		}
		return currentStock.multiply(BigDecimal.valueOf(periodDays))
				.divide(unitsSold, 2, RoundingMode.HALF_UP);
	}
}
