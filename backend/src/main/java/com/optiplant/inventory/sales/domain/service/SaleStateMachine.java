package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.exception.InvalidSaleStateException;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import java.util.Map;

/**
 * State machine for sale lifecycle (R-18, design §4.1).
 *
 * <p>State transitions are represented as a {@link Map} constant so a test can enumerate
 * all states and transitions exhaustively.
 */
public final class SaleStateMachine {

	private static final Map<SaleStatus, Boolean> CANCELLABLE_STATES = Map.of(
			SaleStatus.COMPLETED, true,
			SaleStatus.CANCELLED, false
	);

	private SaleStateMachine() {
	}

	/**
	 * Requires that the sale is in a cancellable state (i.e. {@link SaleStatus#COMPLETED}).
	 *
	 * @param current the current sale status
	 * @throws InvalidSaleStateException if the sale is not in {@code COMPLETED} status (e.g. already {@code CANCELLED})
	 */
	public static void requireCancellable(SaleStatus current) {
		if (!Boolean.TRUE.equals(CANCELLABLE_STATES.get(current))) {
			throw new InvalidSaleStateException("Sale in status " + current + " cannot be cancelled");
		}
	}
}
