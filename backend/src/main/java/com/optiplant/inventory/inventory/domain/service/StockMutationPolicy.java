package com.optiplant.inventory.inventory.domain.service;

import com.optiplant.inventory.inventory.domain.exception.InsufficientStockException;
import com.optiplant.inventory.inventory.domain.exception.UnitCostContractViolationException;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * The single place a branch balance changes (design §3.3). Pure function — no
 * {@code org.springframework..}, no {@code jakarta.persistence..}, no repository call.
 *
 * <p>{@link #apply} returns both the updated {@link BranchInventory} and the
 * {@link KardexMovement.Draft} as one {@link MovementDraft} value: a caller cannot obtain the new
 * balance without also obtaining the movement, which is what makes RN-02 (the balance and its
 * Kardex row commit together) hard to violate by construction, not by reviewer attention.
 */
public final class StockMutationPolicy {

	private static final int SCALE = 4;

	private StockMutationPolicy() {
	}

	/**
	 * @throws UnitCostContractViolationException P-03 — {@code suppliedCost} is present for an
	 *     outbound type, or absent for a type that requires one
	 * @throws InsufficientStockException R-11/RN-01 — an outbound movement would drive
	 *     {@code current_stock} below zero
	 */
	public static MovementDraft apply(BranchInventory current, StockMovementType movementType, Quantity quantity,
			UnitCost suppliedCost, String referenceType, String referenceId, String notes,
			UUID actorUserExternalId, Instant now) {
		UnitCost effectiveCost = resolveCost(movementType, suppliedCost, current.averageCost());

		BigDecimal previousStock = current.currentStock().value();
		BigDecimal resultingStock = movementType.isInbound() ? previousStock.add(quantity.value())
				: previousStock.subtract(quantity.value());
		if (!movementType.isInbound() && resultingStock.signum() < 0) {
			throw new InsufficientStockException(
					"movement of " + quantity.value() + " exceeds the available balance of " + previousStock);
		}

		BigDecimal totalCost = quantity.value().multiply(effectiveCost.value()).setScale(SCALE, RoundingMode.HALF_UP);

		UnitCost resultingAverage = movementType == StockMovementType.PURCHASE_RECEIPT
				? WeightedAverageCostPolicy.recalculate(previousStock, current.averageCost(), quantity, effectiveCost)
				: current.averageCost();
		BranchInventory updated = current.withStockAndCost(new StockLevel(resultingStock), resultingAverage, now);
		KardexMovement.Draft movement = new KardexMovement.Draft(current.branchExternalId(),
				current.productExternalId(), movementType, quantity, effectiveCost, totalCost, previousStock,
				resultingStock, referenceType, referenceId, notes, actorUserExternalId);

		return new MovementDraft(updated, movement);
	}

	private static UnitCost resolveCost(StockMovementType movementType, UnitCost suppliedCost,
			UnitCost currentAverageCost) {
		if (movementType.requiresSuppliedCost()) {
			if (suppliedCost == null) {
				throw new UnitCostContractViolationException("a unit cost is required for " + movementType);
			}
			return suppliedCost;
		}
		if (suppliedCost != null) {
			throw new UnitCostContractViolationException("a unit cost must not be supplied for " + movementType);
		}
		return currentAverageCost;
	}

	/** The updated balance and its matching, not-yet-persisted Kardex movement — always together (RN-02). */
	public record MovementDraft(BranchInventory updated, KardexMovement.Draft movement) {
	}
}
