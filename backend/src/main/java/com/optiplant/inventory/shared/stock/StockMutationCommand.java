package com.optiplant.inventory.shared.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The single write operation of {@link StockMutationPort} (contract §2.2, P-02). Only
 * {@code external_id}-shaped {@link UUID}s cross this port — the implementation resolves
 * the internal numeric foreign keys itself.
 *
 * @param branchExternalId    the branch whose {@code branch_inventories} row is mutated
 * @param productExternalId   the product whose {@code branch_inventories} row is mutated
 * @param movementType        one of the eight {@link StockMovementType} values; its sign is fixed
 * @param quantity             strictly positive, in the product's base unit (RN-13)
 * @param unitCost             REQUIRED when {@code movementType.requiresSuppliedCost()}, MUST be
 *                             {@code null} otherwise (P-03) — the implementation rejects either
 *                             violation before any write
 * @param referenceType        optional free-form reference (e.g. {@code "PURCHASE_ORDER"})
 * @param referenceId          optional free-form reference id
 * @param notes                optional free text
 * @param actorUserExternalId  the responsible user, written to {@code kardex_movements.user_id}
 */
public record StockMutationCommand(UUID branchExternalId, UUID productExternalId, StockMovementType movementType,
		BigDecimal quantity, BigDecimal unitCost, String referenceType, String referenceId, String notes,
		UUID actorUserExternalId) {
}
