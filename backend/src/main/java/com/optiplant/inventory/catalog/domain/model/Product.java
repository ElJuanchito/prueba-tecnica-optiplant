package com.optiplant.inventory.catalog.domain.model;

import com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException;
import com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Domain representation of a catalog product (design §3.3). Immutable: a mutation
 * is a {@code with*} copy, so no use case can hand a half-mutated aggregate to
 * the persistence adapter.
 *
 * <p>Carries {@code external_id} only, never the internal numeric {@code id}
 * (CLAUDE.md's anti-enumeration invariant), and embeds a {@link CategoryRef}
 * rather than a full {@code Category}. {@code updatedAt} is
 * application-maintained: the schema has no trigger, so every {@code with*}
 * advances it except {@link #withUnits}, which does not change the product row.
 *
 * <p>The compact constructor copies {@code units} defensively and then asserts,
 * so no {@code Product} instance can exist that violates them:
 * <ul>
 *   <li>R-13 — no two units share a {@code unitName}
 *       ({@link DuplicateProductUnitException});</li>
 *   <li>R-13 — a unit named like the base unit must have factor {@code 1}
 *       ({@link InvalidConversionFactorException});</li>
 *   <li>R-14 — at most one unit is the default sale unit
 *       ({@link IllegalStateException}).</li>
 * </ul>
 */
public record Product(UUID externalId, Sku sku, String name, String description, UnitCode baseUnit, boolean active,
		CategoryRef category, List<ProductUnit> units, Instant createdAt, Instant updatedAt) {

	public Product {
		units = units == null ? List.of() : List.copyOf(units);

		Set<String> seenNames = new HashSet<>();
		for (ProductUnit unit : units) {
			if (!seenNames.add(unit.unitName().value())) {
				throw new DuplicateProductUnitException(
						"unit '" + unit.unitName().value() + "' is defined more than once on this product");
			}
		}

		if (baseUnit != null) {
			for (ProductUnit unit : units) {
				if (unit.unitName().value().equals(baseUnit.value())
						&& unit.conversionFactor().compareTo(BigDecimal.ONE) != 0) {
					throw new InvalidConversionFactorException("unit '" + unit.unitName().value()
							+ "' is the base unit and must have a conversion factor of 1");
				}
			}
		}

		long defaults = units.stream().filter(ProductUnit::defaultSaleUnit).count();
		if (defaults > 1) {
			throw new IllegalStateException("a product may have at most one default sale unit (R-14)");
		}
	}

	/** Rename / re-describe / move category; {@code updatedAt} advances to {@code now} (R-09). */
	public Product withDetails(Sku sku, String name, String description, CategoryRef category, Instant now) {
		return new Product(externalId, sku, name, description, baseUnit, active, category, units, createdAt, now);
	}

	/** Flip the active flag; {@code updatedAt} advances to {@code now} (R-10, R-11). */
	public Product withActive(boolean active, Instant now) {
		return new Product(externalId, sku, name, description, baseUnit, active, category, units, createdAt, now);
	}

	/** Replace the base unit; {@code updatedAt} advances to {@code now}. Reachable only via
	 *  {@code BaseUnitChangePolicy} (wired in S7). */
	public Product withBaseUnit(UnitCode baseUnit, Instant now) {
		return new Product(externalId, sku, name, description, baseUnit, active, category, units, createdAt, now);
	}

	/** Replace the unit list; the compact constructor re-asserts R-13/R-14. The product row
	 *  itself is unchanged, so {@code updatedAt} is not advanced. */
	public Product withUnits(List<ProductUnit> units) {
		return new Product(externalId, sku, name, description, baseUnit, active, category,
				new ArrayList<>(units), createdAt, updatedAt);
	}
}
