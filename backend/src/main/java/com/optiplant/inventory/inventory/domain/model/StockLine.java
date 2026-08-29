package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the own-branch stock listing (CU-INV-03, contract §6). */
public record StockLine(UUID productExternalId, String sku, String name, BigDecimal currentStock,
		BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock,
		BigDecimal minStockThreshold, BigDecimal averageCost, Instant lastUpdatedAt) {
}
