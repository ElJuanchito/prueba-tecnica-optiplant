package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Enriched view of a sale line item for API responses (contract §6).
 */
public record SaleItemView(
		UUID externalId,
		UUID productExternalId,
		String sku,
		String name,
		BigDecimal quantity,
		BigDecimal listUnitPrice,
		BigDecimal unitPrice,
		BigDecimal discountPercent,
		BigDecimal subtotal
) {
}
