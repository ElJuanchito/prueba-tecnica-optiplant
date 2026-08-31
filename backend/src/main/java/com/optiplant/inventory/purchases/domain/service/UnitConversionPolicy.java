package com.optiplant.inventory.purchases.domain.service;

import com.optiplant.inventory.purchases.domain.exception.UnitConversionUnavailableException;
import com.optiplant.inventory.purchases.domain.model.PurchaseQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Domain conversion to the product's base unit on order entry (R-09, RN-13, design §3.4).
 *
 * <p>A {@code null} unit means the quantity is already in base unit. Otherwise it is multiplied by
 * {@code conversion_factor} at scale 4 {@code HALF_UP}; an absent or non-positive factor is
 * {@link UnitConversionUnavailableException}. {@code purchases} declares its own — importing
 * {@code sales}' would be a {@code purchases -> sales} edge.
 */
public final class UnitConversionPolicy {

	private UnitConversionPolicy() {
	}

	public static PurchaseQuantity toBaseUnit(UUID productExternalId, UUID unitOfMeasureExternalId,
			BigDecimal rawQuantity, BigDecimal conversionFactor) {
		if (rawQuantity == null) {
			throw new IllegalArgumentException("quantity must not be null");
		}
		if (unitOfMeasureExternalId == null) {
			return new PurchaseQuantity(rawQuantity);
		}
		if (conversionFactor == null || conversionFactor.signum() <= 0) {
			throw new UnitConversionUnavailableException(productExternalId, unitOfMeasureExternalId);
		}
		return new PurchaseQuantity(rawQuantity.multiply(conversionFactor).setScale(4, RoundingMode.HALF_UP));
	}
}
