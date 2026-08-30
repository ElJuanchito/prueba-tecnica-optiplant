package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Price list reference descriptor for applied price list in sales (contract §6).
 */
public record PriceListRef(UUID externalId, String code, BigDecimal maxDiscountPercent) {
}
