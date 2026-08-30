package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of one {@code branch_inventories} row (design §3.2): a product's balance
 * in one branch. Immutable — a mutation is a {@code with*} copy.
 *
 * <p>Carries {@code external_id} only, never the internal numeric {@code id}. {@code averageCost}
 * is stamped on outbound movements and recalculated on a {@code PURCHASE_RECEIPT} by
 * {@link com.optiplant.inventory.inventory.domain.service.WeightedAverageCostPolicy} (RN-10),
 * applied through {@link #withStockAndCost(StockLevel, UnitCost, Instant)}.
 */
public record BranchInventory(UUID externalId, UUID branchExternalId, UUID productExternalId,
		StockLevel currentStock, StockLevel reservedStock, StockLevel inTransitStock, StockLevel minStockThreshold,
		UnitCost averageCost, Instant lastUpdatedAt) {

	/** {@code current − reserved}; {@code in_transit_stock} is excluded from it (P-07, RN-04). */
	public BigDecimal availableStock() {
		return currentStock.value().subtract(reservedStock.value());
	}

	/** {@code true} when {@code current_stock <= min_stock_threshold} (R-20). */
	public boolean breachesThreshold() {
		return currentStock.value().compareTo(minStockThreshold.value()) <= 0;
	}

	/** Replaces the current balance, leaving {@code averageCost} untouched; {@code lastUpdatedAt}
	 *  advances to {@code at}. The seven movement types other than {@code PURCHASE_RECEIPT} use this. */
	public BranchInventory withStock(StockLevel newStock, Instant at) {
		return withStockAndCost(newStock, averageCost, at);
	}

	/** Replaces the current balance and the average cost together (RN-10, design §2.4);
	 *  {@code lastUpdatedAt} advances to {@code at}. Only a {@code PURCHASE_RECEIPT} passes a
	 *  {@code newAverageCost} different from the current one. */
	public BranchInventory withStockAndCost(StockLevel newStock, UnitCost newAverageCost, Instant at) {
		return new BranchInventory(externalId, branchExternalId, productExternalId, newStock, reservedStock,
				inTransitStock, minStockThreshold, newAverageCost, at);
	}

	/** Replaces the minimum-stock threshold; {@code lastUpdatedAt} advances to {@code at} (R-14). No
	 *  Kardex row results from this — the balance itself is untouched. */
	public BranchInventory withThreshold(StockLevel newThreshold, Instant at) {
		return new BranchInventory(externalId, branchExternalId, productExternalId, currentStock, reservedStock,
				inTransitStock, newThreshold, averageCost, at);
	}
}
