package com.optiplant.inventory.inventory.domain.model;

import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The response of a manual adjustment or write-off (CU-INV-05, CU-INV-06, contract §6). */
public record MovementReceipt(UUID movementExternalId, StockMovementType movementType, BigDecimal quantity,
		BigDecimal previousStock, BigDecimal resultingStock, Instant createdAt) {
}
