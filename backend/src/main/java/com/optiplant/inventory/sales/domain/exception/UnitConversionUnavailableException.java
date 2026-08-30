package com.optiplant.inventory.sales.domain.exception;

import java.util.UUID;

/**
 * Thrown when the supplied unit of measure cannot be converted to the base unit (R-07).
 * Maps to {@code 400 unit_conversion_unavailable}.
 */
public class UnitConversionUnavailableException extends RuntimeException {

	public UnitConversionUnavailableException(UUID productExternalId, UUID unitOfMeasureExternalId) {
		super("No unit conversion available for product " + productExternalId + " and unit " + unitOfMeasureExternalId);
	}

	public UnitConversionUnavailableException(String message) {
		super(message);
	}
}
