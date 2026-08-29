package com.optiplant.inventory.transfers.domain.model;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive quantity in the product's base unit (RN-13), design §3.1. Normalized to
 * scale 4 {@code HALF_UP}, matching {@code transfer_items.requested_quantity NUMERIC(14,4)} and
 * its {@code CHECK (requested_quantity > 0)}.
 *
 * <p>{@code transfers} declares its own copy rather than importing {@code inventory}'s
 * {@code Quantity} — boundary rule 3 forbids the import.
 */
public record TransferQuantity(BigDecimal value) {

	private static final int SCALE = 4;

	public TransferQuantity {
		if (value == null) {
			throw new InvalidTransferQuantityException("requested quantity must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new InvalidTransferQuantityException("requested quantity must be strictly positive");
		}
	}
}
