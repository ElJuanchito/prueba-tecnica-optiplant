package com.optiplant.inventory.transfers.domain.model;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative quantity resulting from a transfer transition — {@code dispatched_quantity},
 * {@code received_quantity} or {@code discrepancy_quantity} (design §3.1). Normalized to scale 4
 * {@code HALF_UP}, matching the {@code NUMERIC(14,4)} columns it mirrors.
 *
 * <p>Zero is a legal value by design: R-19 makes a full-loss receipt (zero received) valid, not
 * an error, and every item starts at zero before dispatch (schema default).
 */
public record SettledQuantity(BigDecimal value) {

	private static final int SCALE = 4;

	public SettledQuantity {
		if (value == null) {
			throw new InvalidTransferQuantityException("a settled quantity must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new InvalidTransferQuantityException("a settled quantity must not be negative");
		}
	}

	public static SettledQuantity zero() {
		return new SettledQuantity(BigDecimal.ZERO);
	}
}
