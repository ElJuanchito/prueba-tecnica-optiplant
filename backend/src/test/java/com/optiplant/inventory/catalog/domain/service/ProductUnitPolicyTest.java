package com.optiplant.inventory.catalog.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException;
import com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException;
import com.optiplant.inventory.catalog.domain.exception.ProductUnitNotFoundException;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.Sku;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProductUnitPolicy} (R-13, R-14): factor {@code 0}/{@code
 * -1} rejected, a base-unit homonym accepted only with factor 1, marking a new
 * default leaves exactly one, removing the default leaves none, and a duplicate
 * {@code unitName} is rejected. No Docker — pure domain.
 */
class ProductUnitPolicyTest {

	private static final Instant TS = Instant.EPOCH;
	private static final CategoryRef CATEGORY = new CategoryRef(UUID.randomUUID(), "Fertilizantes", true);

	private static ProductUnit unit(String name, String factor, boolean defaultSaleUnit) {
		return new ProductUnit(UUID.randomUUID(), new UnitCode(name), new BigDecimal(factor), defaultSaleUnit, TS);
	}

	private static Product product(UnitCode baseUnit, List<ProductUnit> units) {
		return new Product(UUID.randomUUID(), new Sku("FERT-NPK-151515"), "Fertilizante Triple 15", null, baseUnit,
				true, CATEGORY, units, TS, TS);
	}

	@Test
	void rejectsFactorZeroOrNegativeOnReplace() {
		ProductUnit existing = unit("CAJA", "12", false);
		Product base = product(UnitCode.baseUnit("KG"), List.of(existing));

		assertThatThrownBy(() -> ProductUnitPolicy.replaceUnit(base, existing.externalId(), new UnitCode("CAJA"),
				new BigDecimal("0"), false)).isInstanceOf(InvalidConversionFactorException.class);
		assertThatThrownBy(() -> ProductUnitPolicy.replaceUnit(base, existing.externalId(), new UnitCode("CAJA"),
				new BigDecimal("-1"), false)).isInstanceOf(InvalidConversionFactorException.class);
	}

	@Test
	void rejectsABaseUnitHomonymWhoseFactorIsNotOne() {
		Product base = product(UnitCode.baseUnit("KG"), List.of());

		assertThatThrownBy(() -> ProductUnitPolicy.addUnit(base, unit("KG", "2", false)))
				.isInstanceOf(InvalidConversionFactorException.class);
	}

	@Test
	void acceptsABaseUnitHomonymWhoseFactorIsExactlyOne() {
		Product base = product(UnitCode.baseUnit("KG"), List.of());

		Product result = ProductUnitPolicy.addUnit(base, unit("KG", "1.0000", false));

		assertThat(result.units()).extracting(u -> u.unitName().value()).containsExactly("KG");
	}

	@Test
	void markingANewDefaultViaAddLeavesExactlyOneDefault() {
		ProductUnit current = unit("CAJA", "12", true);
		Product base = product(UnitCode.baseUnit("KG"), List.of(current));

		Product result = ProductUnitPolicy.addUnit(base, unit("SACO", "50", true));

		assertThat(result.units()).filteredOn(ProductUnit::defaultSaleUnit)
				.extracting(u -> u.unitName().value()).containsExactly("SACO");
	}

	@Test
	void markingANewDefaultViaReplaceClearsTheSibling() {
		ProductUnit a = unit("CAJA", "12", true);
		ProductUnit b = unit("SACO", "50", false);
		Product base = product(UnitCode.baseUnit("KG"), List.of(a, b));

		Product result = ProductUnitPolicy.replaceUnit(base, b.externalId(), new UnitCode("SACO"),
				new BigDecimal("50"), true);

		assertThat(result.units()).filteredOn(ProductUnit::defaultSaleUnit)
				.extracting(u -> u.unitName().value()).containsExactly("SACO");
	}

	@Test
	void removingTheCurrentDefaultLeavesTheProductWithNone() {
		ProductUnit a = unit("CAJA", "12", true);
		ProductUnit b = unit("SACO", "50", false);
		Product base = product(UnitCode.baseUnit("KG"), List.of(a, b));

		Product result = ProductUnitPolicy.removeUnit(base, a.externalId());

		assertThat(result.units()).extracting(u -> u.unitName().value()).containsExactly("SACO");
		assertThat(result.units()).extracting(ProductUnit::defaultSaleUnit).containsOnly(false);
	}

	@Test
	void rejectsADuplicateUnitName() {
		Product base = product(UnitCode.baseUnit("KG"), List.of(unit("CAJA", "12", false)));

		assertThatThrownBy(() -> ProductUnitPolicy.addUnit(base, unit("CAJA", "24", false)))
				.isInstanceOf(DuplicateProductUnitException.class);
	}

	@Test
	void replacingOrRemovingAnUnknownUnitThrowsProductUnitNotFound() {
		Product base = product(UnitCode.baseUnit("KG"), List.of(unit("CAJA", "12", false)));

		assertThatThrownBy(() -> ProductUnitPolicy.removeUnit(base, UUID.randomUUID()))
				.isInstanceOf(ProductUnitNotFoundException.class);
		assertThatThrownBy(() -> ProductUnitPolicy.replaceUnit(base, UUID.randomUUID(), new UnitCode("CAJA"),
				new BigDecimal("12"), false)).isInstanceOf(ProductUnitNotFoundException.class);
	}
}
