package com.optiplant.inventory.inventory.domain.service;

import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The whole of RN-10 (design §2.2, R-18), and the only place the weighted-average-cost formula
 * exists. Pure Java — no {@code org.springframework..}, no {@code jakarta.persistence..}, no
 * repository call — so it stays inside the domain and can be unit-tested without Spring.
 *
 * <p>Its own class rather than a private method of {@link StockMutationPolicy} (D-1): RN-10 is a
 * business rule with a worked example (HU-INV-03) and deserves its own test target.
 */
public final class WeightedAverageCostPolicy {

	/**
	 * Division scale used before {@link UnitCost} re-rounds to scale 4 (D-2): dividing at scale 8
	 * first avoids the double rounding a direct scale-4 division would introduce. HU-INV-03's
	 * worked example is exact either way.
	 */
	private static final int INTERMEDIATE_SCALE = 8;

	private WeightedAverageCostPolicy() {
	}

	/**
	 * Recalculates the branch's weighted average cost after an inbound receipt:
	 * {@code ((previousStock × previousAverage) + (receivedQuantity × receivedCost))
	 * / (previousStock + receivedQuantity)} (RN-10, R-18).
	 *
	 * <p>{@code CHECK (current_stock >= 0)} makes only {@code 0} reachable on the zero-balance
	 * guard, and {@link Quantity} guarantees {@code receivedQuantity > 0}, so no division by zero
	 * is possible.
	 *
	 * @param previousStock    the branch balance before the receipt, {@code >= 0}
	 * @param previousAverage   the branch average cost before the receipt
	 * @param receivedQuantity  the strictly positive quantity received
	 * @param receivedCost      the effective acquisition unit cost of the receipt (R-17)
	 * @return the new average cost; when {@code previousStock <= 0}, the received cost itself
	 */
	public static UnitCost recalculate(BigDecimal previousStock, UnitCost previousAverage,
			Quantity receivedQuantity, UnitCost receivedCost) {
		if (previousStock.signum() <= 0) {
			return receivedCost;
		}
		BigDecimal weightedTotal = previousStock.multiply(previousAverage.value())
				.add(receivedQuantity.value().multiply(receivedCost.value()));
		BigDecimal newQuantity = previousStock.add(receivedQuantity.value());
		return new UnitCost(weightedTotal.divide(newQuantity, INTERMEDIATE_SCALE, RoundingMode.HALF_UP));
	}
}
