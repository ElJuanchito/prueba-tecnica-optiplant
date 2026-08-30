package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.UnitConversionUnavailableException;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitConversionPolicyTest {

	private static final UUID PRODUCT_ID = UUID.randomUUID();
	private static final UUID UNIT_ID = UUID.randomUUID();

	@Test
	@DisplayName("R-07 / RN-13: When no alternative unit is specified, quantity is already in base unit")
	void noUnitSpecifiedIsAlreadyBaseUnit() {
		SaleQuantity qty = UnitConversionPolicy.toBaseUnit(PRODUCT_ID, null, new BigDecimal("10.5000"), null);

		assertThat(qty.value()).isEqualByComparingTo("10.5000");
	}

	@Test
	@DisplayName("R-07 / RN-13: Alternative unit is converted to base unit via conversion factor")
	void alternativeUnitConvertedToBaseUnit() {
		// e.g. 5 boxes with conversion factor of 12 units/box = 60 units
		SaleQuantity qty = UnitConversionPolicy.toBaseUnit(
				PRODUCT_ID,
				UNIT_ID,
				new BigDecimal("5.0000"),
				new BigDecimal("12.0000")
		);

		assertThat(qty.value()).isEqualByComparingTo("60.0000");
	}

	@Test
	@DisplayName("R-07: Alternative unit with missing conversion factor throws UnitConversionUnavailableException")
	void missingConversionFactorThrows() {
		assertThatThrownBy(() -> UnitConversionPolicy.toBaseUnit(
				PRODUCT_ID,
				UNIT_ID,
				new BigDecimal("5.0000"),
				null
		)).isInstanceOf(UnitConversionUnavailableException.class);
	}
}
