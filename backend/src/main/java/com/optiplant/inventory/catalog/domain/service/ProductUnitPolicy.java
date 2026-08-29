package com.optiplant.inventory.catalog.domain.service;

import com.optiplant.inventory.catalog.domain.exception.ProductUnitNotFoundException;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure functions over {@link Product} that transform its unit list while keeping
 * the R-13/R-14 invariants (design §4.1). {@link Product}'s compact constructor is
 * what <em>asserts</em> the invariants; this service is what <em>transforms</em>,
 * and every method returns a new {@link Product}. Framework-free — no
 * {@code org.springframework..}, no {@code jakarta.persistence..}.
 *
 * <ul>
 *   <li>{@link #addUnit} / {@link #replaceUnit}: when the incoming unit carries
 *       {@code defaultSaleUnit = true}, the flag is cleared on every sibling
 *       <em>before</em> the new one is applied, so the returned {@link Product}
 *       always satisfies R-14 ("at most one default sale unit").</li>
 *   <li>A unit whose {@code unitName} equals the product's base unit is accepted
 *       only with {@code conversionFactor == 1} (R-13) — enforced by the
 *       {@link Product} constructor via {@code withUnits}.</li>
 *   <li>{@link #removeUnit} on the current default is allowed and legitimately
 *       leaves the product with no default at all (R-14's fourth scenario: the
 *       mark is optional). No other table references {@code product_units}.</li>
 * </ul>
 */
public final class ProductUnitPolicy {

	private ProductUnitPolicy() {
	}

	/** Adds {@code unit}; clears the default flag on every sibling first when {@code unit} is a default. */
	public static Product addUnit(Product product, ProductUnit unit) {
		List<ProductUnit> next = new ArrayList<>(product.units());
		if (unit.defaultSaleUnit()) {
			next.replaceAll(ProductUnitPolicy::withoutDefault);
		}
		next.add(unit);
		return product.withUnits(next);
	}

	/**
	 * Replaces the unit identified by {@code unitExternalId} with the given name,
	 * factor and default flag. When the replacement is a default, every other
	 * sibling is cleared first.
	 *
	 * @throws ProductUnitNotFoundException when no unit of {@code product} has that id
	 */
	public static Product replaceUnit(Product product, UUID unitExternalId, UnitCode name, BigDecimal factor,
			boolean defaultSaleUnit) {
		ProductUnit target = require(product, unitExternalId);
		ProductUnit replacement = new ProductUnit(target.externalId(), name, factor, defaultSaleUnit,
				target.createdAt());

		List<ProductUnit> next = new ArrayList<>();
		for (ProductUnit current : product.units()) {
			if (current.externalId().equals(unitExternalId)) {
				continue;
			}
			next.add(defaultSaleUnit ? withoutDefault(current) : current);
		}
		next.add(replacement);
		return product.withUnits(next);
	}

	/**
	 * Removes the unit identified by {@code unitExternalId}. Removing the current
	 * default simply leaves the product with none.
	 *
	 * @throws ProductUnitNotFoundException when no unit of {@code product} has that id
	 */
	public static Product removeUnit(Product product, UUID unitExternalId) {
		require(product, unitExternalId);
		List<ProductUnit> next = product.units().stream()
				.filter(unit -> !unit.externalId().equals(unitExternalId))
				.toList();
		return product.withUnits(next);
	}

	private static ProductUnit withoutDefault(ProductUnit unit) {
		return unit.defaultSaleUnit()
				? new ProductUnit(unit.externalId(), unit.unitName(), unit.conversionFactor(), false, unit.createdAt())
				: unit;
	}

	private static ProductUnit require(Product product, UUID unitExternalId) {
		return product.units().stream()
				.filter(unit -> unit.externalId().equals(unitExternalId))
				.findFirst()
				.orElseThrow(() -> new ProductUnitNotFoundException(unitExternalId));
	}
}
