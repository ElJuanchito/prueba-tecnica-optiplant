package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Stock impact of active transfers on a product (CU-DSH-01, RF-DSH-03, R-13, R-14).
 */
public record TransferStockImpact(UUID productExternalId, String sku, String name, BigDecimal currentStock,
		BigDecimal inTransitStock, BigDecimal inboundInTransit, BigDecimal outboundCommitted,
		BigDecimal projectedStock) {
}
