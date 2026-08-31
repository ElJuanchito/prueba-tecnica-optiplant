package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;

/**
 * Severity level for critical replenishment (CU-DSH-02, RF-DSH-04, R-16).
 */
public enum ReplenishmentSeverity {
	OUT_OF_STOCK, CRITICAL;

	/**
	 * R-16: OUT_OF_STOCK when current_stock <= 0, CRITICAL otherwise.
	 */
	public static ReplenishmentSeverity of(BigDecimal currentStock) {
		if (currentStock == null || currentStock.compareTo(BigDecimal.ZERO) <= 0) {
			return OUT_OF_STOCK;
		}
		return CRITICAL;
	}
}
