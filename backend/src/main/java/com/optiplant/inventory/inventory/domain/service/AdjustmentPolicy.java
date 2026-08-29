package com.optiplant.inventory.inventory.domain.service;

import com.optiplant.inventory.inventory.domain.exception.AdjustmentWithoutDifferenceException;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;

/**
 * Turns a manual adjustment's counted physical quantity into a signed movement (CU-INV-05,
 * design §3.3). Pure function, no dependency on {@link com.optiplant.inventory.inventory.domain.model.BranchInventory}
 * — it only needs the current numeric balance.
 */
public final class AdjustmentPolicy {

	private AdjustmentPolicy() {
	}

	/**
	 * @throws IllegalArgumentException {@code countedQuantity} is {@code null} or negative (RN-01)
	 * @throws AdjustmentWithoutDifferenceException {@code countedQuantity} equals
	 *     {@code currentBalance} (R-08) — a no-op is not an audit event
	 */
	public static AdjustmentDecision decide(BigDecimal currentBalance, BigDecimal countedQuantity) {
		if (countedQuantity == null || countedQuantity.signum() < 0) {
			throw new IllegalArgumentException("counted quantity must not be negative");
		}
		int comparison = countedQuantity.compareTo(currentBalance);
		if (comparison == 0) {
			throw new AdjustmentWithoutDifferenceException();
		}
		BigDecimal difference = countedQuantity.subtract(currentBalance).abs();
		StockMovementType movementType = comparison > 0 ? StockMovementType.ADJUSTMENT_POS
				: StockMovementType.ADJUSTMENT_NEG;
		return new AdjustmentDecision(movementType, new Quantity(difference));
	}

	/** R-06: balance 100, count 92 → {@code ADJUSTMENT_NEG} of quantity 8. */
	public record AdjustmentDecision(StockMovementType movementType, Quantity quantity) {
	}
}
