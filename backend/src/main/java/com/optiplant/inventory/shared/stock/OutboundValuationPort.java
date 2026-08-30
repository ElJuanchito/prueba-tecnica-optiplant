package com.optiplant.inventory.shared.stock;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Answers the unit cost stamped on an outbound Kardex movement, keyed by the reference columns
 * every {@code TRANSFER_OUT} already carries (contract D-2, design §2.2). R-20 requires a
 * received item to be valued at the same unit cost its matching {@code TRANSFER_OUT} used, and
 * {@code transfer_items} has no column to cache it in (§2.5) — this port reads it back from
 * {@code kardex_movements} instead.
 *
 * <p>Implemented by {@code inventory}'s {@code InventoryOutboundValuationAdapter}: a read method
 * has no business on {@link StockMutationPort}, a write port.
 */
public interface OutboundValuationPort {

	/**
	 * Batch lookup — one call per receipt, no per-item query (RNF-PER-01).
	 *
	 * @param branchExternalId the branch whose outbound Kardex rows are read (the transfer's origin)
	 * @param referenceType    the Kardex {@code reference_type} written at dispatch (e.g. {@code "TRANSFER"})
	 * @param referenceId      the Kardex {@code reference_id} written at dispatch (the transfer's
	 *                         {@code external_id})
	 * @return product {@code external_id} to the unit cost of the outbound movement carrying that reference
	 */
	Map<UUID, BigDecimal> outboundUnitCosts(UUID branchExternalId, String referenceType, String referenceId);
}
