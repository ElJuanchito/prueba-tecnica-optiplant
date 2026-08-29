package com.optiplant.inventory.shared.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Shifts a branch's {@code in_transit_stock} without writing a Kardex row (P-06). Used by
 * transfer dispatch/receipt (P-05): dispatch increments the destination branch's in-transit
 * quantity, receipt decrements it. This is a second, explicitly named operation on
 * {@link StockMutationPort} — never a side effect of {@link StockMutationPort#applyMovement}.
 */
public record InTransitShiftCommand(UUID branchExternalId, UUID productExternalId, BigDecimal quantity,
		InTransitDirection direction, UUID actorUserExternalId) {
}
