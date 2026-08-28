package com.optiplant.inventory.shared.audit;

/**
 * Every mutation writes its audit entry through this port, invoked inside the
 * caller's own transaction — CLAUDE.md: "toda mutación de stock escribe su movimiento
 * en el Kardex en la misma transacción" generalizes here to every audited mutation,
 * and "los efectos atómicos van por puerto de salida síncrono, nunca por evento": no
 * implementation of this port may be {@code @Async} or defer the write to {@code
 * AFTER_COMMIT}. If the write fails, the triggering mutation must not be persisted
 * either — both live or die together in one transaction.
 */
public interface AuditWritePort {

	void record(AuditEntryCommand command);
}
