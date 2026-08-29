package com.optiplant.inventory.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.domain.exception.DuplicateProductUnitException;
import com.optiplant.inventory.catalog.domain.exception.InvalidConversionFactorException;
import com.optiplant.inventory.catalog.domain.exception.MultipleDefaultSaleUnitsException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Product}'s compact constructor (R-13, R-14): no
 * {@code Product} instance can exist with two units of the same name, with a
 * base-unit homonym carrying a factor other than 1, or with two default sale
 * units.
 */
class ProductInvariantsTest {

	private static final Instant TS = Instant.EPOCH;
	private static final CategoryRef CATEGORY = new CategoryRef(UUID.randomUUID(), "Fertilizantes", true);

	private static ProductUnit unit(String name, String factor, boolean defaultSaleUnit) {
		return new ProductUnit(UUID.randomUUID(), new UnitCode(name), new BigDecimal(factor), defaultSaleUnit, TS);
	}

	private static Product productWith(UnitCode baseUnit, List<ProductUnit> units) {
		return new Product(UUID.randomUUID(), new Sku("FERT-NPK-151515"), "Fertilizante Triple 15", null, baseUnit,
				true, CATEGORY, units, TS, TS);
	}

	@Test
	void rejectsTwoUnitsWithTheSameName() {
		assertThatThrownBy(() -> productWith(UnitCode.baseUnit("KG"),
				List.of(unit("CAJA", "12", false), unit("CAJA", "24", false))))
				.isInstanceOf(DuplicateProductUnitException.class);
	}

	@Test
	void rejectsABaseUnitHomonymWhoseFactorIsNotOne() {
		assertThatThrownBy(() -> productWith(UnitCode.baseUnit("KG"), List.of(unit("KG", "2", false))))
				.isInstanceOf(InvalidConversionFactorException.class);
	}

	@Test
	void acceptsABaseUnitHomonymWhoseFactorIsExactlyOne() {
		Product product = productWith(UnitCode.baseUnit("KG"), List.of(unit("KG", "1.0000", false)));

		assertThat(product.units()).hasSize(1);
	}

	@Test
	void rejectsTwoDefaultSaleUnits() {
		assertThatThrownBy(() -> productWith(UnitCode.baseUnit("KG"),
				List.of(unit("CAJA", "12", true), unit("SACO", "50", true))))
				.isInstanceOf(MultipleDefaultSaleUnitsException.class);
	}

	@Test
	void acceptsAtMostOneDefaultSaleUnit() {
		Product product = productWith(UnitCode.baseUnit("KG"),
				List.of(unit("CAJA", "12", true), unit("SACO", "50", false)));

		assertThat(product.units()).hasSize(2);
	}

	@Test
	void acceptsAProductWithNoDefaultSaleUnitAtAll() {
		Product product = productWith(UnitCode.baseUnit("KG"),
				List.of(unit("CAJA", "12", false), unit("SACO", "50", false)));

		assertThat(product.units()).extracting(ProductUnit::defaultSaleUnit).containsOnly(false);
	}

	@Test
	void rejectsANonPositiveConversionFactorAtTheUnitLevel() {
		assertThatThrownBy(() -> unit("CAJA", "0", false)).isInstanceOf(InvalidConversionFactorException.class);
		assertThatThrownBy(() -> unit("CAJA", "-1", false)).isInstanceOf(InvalidConversionFactorException.class);
	}

	@Test
	void copiesTheUnitListDefensively() {
		List<ProductUnit> mutable = new ArrayList<>(List.of(unit("CAJA", "12", false)));
		Product product = productWith(UnitCode.baseUnit("KG"), mutable);

		mutable.clear();

		assertThat(product.units()).hasSize(1);
	}
}
