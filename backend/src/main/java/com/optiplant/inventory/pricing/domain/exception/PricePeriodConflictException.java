package com.optiplant.inventory.pricing.domain.exception;

/**
 * Thrown when setting a new price conflicts with an existing validity period (R-16, D-7).
 * Maps to {@code 409 price_period_conflict}.
 */
public class PricePeriodConflictException extends RuntimeException {

	public PricePeriodConflictException(String message) {
		super(message);
	}
}
