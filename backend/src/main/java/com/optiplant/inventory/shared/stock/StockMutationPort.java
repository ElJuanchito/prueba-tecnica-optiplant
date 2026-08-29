package com.optiplant.inventory.shared.stock;

import java.util.UUID;

/**
 * The synchronous stock-mutation port (contract §2.2, D-2). {@code purchases}, {@code sales}
 * and {@code transfers} write stock through this port without importing {@code inventory}, so
 * the module graph gains only {@code X → shared ← inventory} and stays acyclic — same shape and
 * rationale as {@code shared/audit/AuditWritePort} and {@code shared/stock/ProductStockPresencePort}.
 *
 * <p><strong>No implementation of this port may be {@code @Async}, deferred to
 * {@code AFTER_COMMIT}, or split into two separate writes.</strong> {@link #applyMovement} MUST,
 * in one call inside the caller's own transaction, mutate {@code branch_inventories.current_stock}
 * and insert the matching {@code kardex_movements} row (RN-02, RNF-INT-01) — CLAUDE.md: "toda
 * mutación de stock escribe su movimiento en el Kardex en la misma transacción" and "los efectos
 * atómicos van por puerto de salida síncrono, nunca por evento".
 */
public interface StockMutationPort {

	/**
	 * Mutates {@code current_stock} and inserts the matching Kardex row atomically, joining the
	 * caller's own transaction (P-01) — {@code Propagation.REQUIRED}, never {@code REQUIRES_NEW}.
	 *
	 * @return the created movement's {@code external_id}
	 */
	UUID applyMovement(StockMutationCommand command);

	/**
	 * Increments or decrements the destination branch's {@code in_transit_stock} (P-05, P-06).
	 * Writes no Kardex row and does not touch {@code current_stock} — this is not a stock
	 * mutation, so RN-02 is not engaged.
	 */
	void shiftInTransit(InTransitShiftCommand command);
}
