package com.optiplant.inventory.inventory.domain.model;

import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of one persisted {@code kardex_movements} row (design §3.2).
 *
 * <p>Has <strong>no</strong> {@code with*} method and no setter, and
 * {@code KardexRepositoryPort} exposes no update or delete: R-17's append-only guarantee is a
 * property of the type system, not a convention (RN-12, RNF-INT-02).
 */
public record KardexMovement(UUID externalId, UUID branchExternalId, UUID productExternalId,
		StockMovementType movementType, Quantity quantity, UnitCost unitCost, BigDecimal totalCost,
		BigDecimal previousStock, BigDecimal resultingStock, String referenceType, String referenceId, String notes,
		UUID userExternalId, Instant createdAt) {

	/**
	 * The pre-insert shape produced by {@code StockMutationPolicy}: everything a
	 * {@code KardexMovement} carries except the fields the persistence adapter assigns at insert
	 * time ({@code externalId}, {@code createdAt}).
	 */
	public record Draft(UUID branchExternalId, UUID productExternalId, StockMovementType movementType,
			Quantity quantity, UnitCost unitCost, BigDecimal totalCost, BigDecimal previousStock,
			BigDecimal resultingStock, String referenceType, String referenceId, String notes,
			UUID userExternalId) {
	}
}
