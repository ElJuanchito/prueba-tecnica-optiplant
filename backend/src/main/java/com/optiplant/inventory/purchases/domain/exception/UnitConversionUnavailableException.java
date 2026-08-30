package com.optiplant.inventory.purchases.domain.exception;

import java.util.UUID;

/**
 * The supplied unit has no conversion to the product's base unit (R-09, RN-13). Maps to
 * {@code 400 unit_conversion_unavailable}. {@code purchases} declares its own — importing
 * {@code sales}' would be a {@code purchases -> sales} edge (design §3.4).
 */
public class UnitConversionUnavailableException extends RuntimeException {

	public UnitConversionUnavailableException(UUID productExternalId, UUID unitOfMeasureExternalId) {
		super("No unit conversion available for product " + productExternalId + " and unit " + unitOfMeasureExternalId);
	}

	public UnitConversionUnavailableException(String message) {
		super(message);
	}
}
