package com.optiplant.inventory.inventory.domain.model;

import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the Kardex history (CU-INV-08, contract §6, R-16). */
public record KardexLine(UUID externalId, UUID productExternalId, StockMovementType movementType,
		BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, BigDecimal previousStock,
		BigDecimal resultingStock, String referenceType, String referenceId, String notes, UUID userExternalId,
		Instant createdAt) {
}
