package com.optiplant.inventory.catalog.domain.model;

import com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An alternative unit of measure of a product, with its conversion factor
 * expressed in base units (R-13). Immutable.
 *
 * <p>The compact constructor rejects a {@code null} or non-positive
 * {@code conversionFactor} with {@link InvalidConversionFactorException}. The
 * factor is a {@link BigDecimal} — never a {@code double}, which cannot represent
 * a decimal factor exactly and would drift the moment {@code inventory}
 * multiplies by it ({@code product_units.conversion_factor NUMERIC(12,4)}).
 *
 * <p>The base-unit homonym rule (a unit named like the product's base unit must
 * have factor {@code 1}) is cross-unit and is asserted by {@link Product}, which
 * is the only place that knows the base unit.
 */
public record ProductUnit(UUID externalId, UnitCode unitName, BigDecimal conversionFactor, boolean defaultSaleUnit,
		Instant createdAt) {

	public ProductUnit {
		if (conversionFactor == null || conversionFactor.signum() <= 0) {
			throw new InvalidConversionFactorException(
					"conversion factor of unit " + (unitName == null ? "?" : unitName.value())
							+ " must be a positive number");
		}
	}
}
