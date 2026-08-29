package com.optiplant.inventory.transfers.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/** One item enriched with its product's {@code sku}/{@code name} for the detail response (contract §6). */
public record TransferItemView(UUID externalId, UUID productExternalId, String sku, String name,
		BigDecimal requestedQuantity, BigDecimal dispatchedQuantity, BigDecimal receivedQuantity,
		BigDecimal discrepancyQuantity, String discrepancyReason) {
}
