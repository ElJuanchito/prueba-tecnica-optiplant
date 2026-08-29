package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** The response of setting a product's minimum-stock threshold (CU-INV-07, contract §6). */
public record ThresholdView(UUID productExternalId, BigDecimal minStockThreshold) {
}
