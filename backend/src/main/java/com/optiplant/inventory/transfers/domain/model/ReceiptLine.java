package com.optiplant.inventory.transfers.domain.model;

import java.util.UUID;

/**
 * One item's resolved receipt outcome, produced by
 * {@link com.optiplant.inventory.transfers.domain.service.TransferReceiptPolicy} — RN-06 holds
 * by construction here: {@code discrepancyQuantity} is always {@code dispatched - received},
 * never a second client-supplied input.
 */
public record ReceiptLine(UUID itemExternalId, UUID productExternalId, SettledQuantity receivedQuantity,
		SettledQuantity discrepancyQuantity, String discrepancyReason) {
}
