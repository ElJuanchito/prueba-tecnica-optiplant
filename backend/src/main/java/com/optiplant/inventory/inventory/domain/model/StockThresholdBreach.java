package com.optiplant.inventory.inventory.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The P-08 domain event: a committed movement (or a committed threshold change, R-15) left
 * {@code current_stock <= min_stock_threshold}. {@code AlertRaisingPolicy} renders this into a
 * {@code shared/alert/OperationalAlertRaised} whose {@code subjectToken} is
 * {@code productExternalId} and whose {@code message} carries every field below in
 * human-readable form — every P-08 field survives the transport generalization (design §2.2, D-1).
 *
 * @param movementExternalId the triggering movement's {@code external_id}, or {@code null} when
 *                            the breach was evaluated after a pure threshold change (R-15) with
 *                            no movement involved
 */
public record StockThresholdBreach(UUID branchExternalId, UUID productExternalId, BigDecimal resultingStock,
		BigDecimal threshold, UUID movementExternalId) {
}
