package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.exception.UnitConversionUnavailableException;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Domain conversion to base unit on sale entry (R-07, RN-13, design §4.1).
 *
 * <p>If no alternative unit of measure is provided, the quantity is already in base unit.
 * If an alternative unit is provided, it is multiplied by {@code conversion_factor} at scale 4 {@code HALF_UP}.
 */
public final class UnitConversionPolicy {

	private UnitConversionPolicy() {
	}

	/**
	 * Converts the given quantity to base unit using the supplied conversion factor.
	 *
	 * @param productExternalId       product identifier
	 * @param unitOfMeasureExternalId alternative unit identifier, nullable if already base unit
	 * @param rawQuantity             quantity in the specified unit
	 * @param conversionFactor        multiplication factor to base unit, required if alternative unit is specified
	 * @return {@link SaleQuantity} in base unit
	 * @throws UnitConversionUnavailableException if unit is specified but no conversion factor is available
	 */
	public static SaleQuantity toBaseUnit(
			UUID productExternalId,
			UUID unitOfMeasureExternalId,
			BigDecimal rawQuantity,
			BigDecimal conversionFactor
	) {
		if (rawQuantity == null) {
			throw new IllegalArgumentException("rawQuantity must not be null");
		}
		if (unitOfMeasureExternalId == null) {
			return new SaleQuantity(rawQuantity);
		}
		if (conversionFactor == null) {
			throw new UnitConversionUnavailableException(productExternalId, unitOfMeasureExternalId);
		}
		if (conversionFactor.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("conversionFactor must be strictly positive");
		}
		BigDecimal converted = rawQuantity.multiply(conversionFactor).setScale(4, RoundingMode.HALF_UP);
		return new SaleQuantity(converted);
	}
}
