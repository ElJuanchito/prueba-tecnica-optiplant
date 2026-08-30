package com.optiplant.inventory.shared.stock;

import java.util.UUID;

/**
 * {@link StockMutationPort}'s contractual failure mode (contract D-4, design §2.2).
 * {@code applyMovement} refuses an overdraw by throwing {@code inventory}'s own
 * {@code InsufficientStockException} — a type {@code transfers} cannot catch, since boundary
 * rule 3 forbids importing another module's package. A port whose failure mode is inexpressible
 * to its callers is an incomplete port, so this unchecked exception travels through
 * {@code shared} instead.
 *
 * <p>{@code inventory}'s {@code StockMutationAdapter} — the port implementation, not the
 * domain — translates its own domain exceptions into this one on the way out, leaving
 * {@code inventory}'s own use cases and exception handler untouched. Every module consuming
 * {@link StockMutationPort} maps {@link Reason#INSUFFICIENT_STOCK} to its own {@code 409}
 * (e.g. {@code transfers}' {@code insufficient_stock}, R-12).
 */
public class StockMutationRejectedException extends RuntimeException {

	private final Reason reason;

	public StockMutationRejectedException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	/** Why {@link StockMutationPort#applyMovement} or {@link StockMutationPort#shiftInTransit} refused. */
	public enum Reason {
		/** An outbound movement would drive {@code current_stock} below zero (R-11, RN-01). */
		INSUFFICIENT_STOCK,
		/** {@code branchExternalId} names no branch, or an inactive one. */
		UNKNOWN_BRANCH,
		/** {@code productExternalId} names no product, or a disabled one. */
		UNKNOWN_PRODUCT,
		/** {@code unitCost} was supplied for a type that forbids it, or omitted for one that requires it (P-03). */
		UNIT_COST_CONTRACT
	}

	public static StockMutationRejectedException unknownBranch(UUID branchExternalId) {
		return new StockMutationRejectedException(Reason.UNKNOWN_BRANCH,
				"No active branch found for external id " + branchExternalId);
	}

	public static StockMutationRejectedException unknownProduct(UUID productExternalId) {
		return new StockMutationRejectedException(Reason.UNKNOWN_PRODUCT,
				"No active product found for external id " + productExternalId);
	}
}
