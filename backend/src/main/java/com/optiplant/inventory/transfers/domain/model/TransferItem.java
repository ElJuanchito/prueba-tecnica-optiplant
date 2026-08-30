package com.optiplant.inventory.transfers.domain.model;

import java.util.UUID;

/**
 * Domain representation of one {@code transfer_items} row (design §3.2). Immutable — every field
 * change is a new instance, produced by {@link Transfer}'s mutators after consulting the
 * relevant domain-service policy. {@code dispatchedQuantity}, {@code receivedQuantity} and
 * {@code discrepancyQuantity} start at {@link SettledQuantity#zero()}, matching the schema's
 * {@code DEFAULT 0.0000}.
 */
public record TransferItem(UUID externalId, UUID productExternalId, TransferQuantity requestedQuantity,
		SettledQuantity dispatchedQuantity, SettledQuantity receivedQuantity, SettledQuantity discrepancyQuantity,
		String discrepancyReason) {

	public TransferItem {
		dispatchedQuantity = dispatchedQuantity == null ? SettledQuantity.zero() : dispatchedQuantity;
		receivedQuantity = receivedQuantity == null ? SettledQuantity.zero() : receivedQuantity;
		discrepancyQuantity = discrepancyQuantity == null ? SettledQuantity.zero() : discrepancyQuantity;
	}

	/** A brand-new requested item, before approval, dispatch or receipt. */
	public static TransferItem requested(UUID externalId, UUID productExternalId, TransferQuantity requestedQuantity) {
		return new TransferItem(externalId, productExternalId, requestedQuantity, SettledQuantity.zero(),
				SettledQuantity.zero(), SettledQuantity.zero(), null);
	}
}
