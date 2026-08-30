package com.optiplant.inventory.purchases.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.UnitConversionUnavailableException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitConversionPolicyTest {

	private static final UUID PRODUCT = UUID.randomUUID();
	private static final UUID UNIT = UUID.randomUUID();

	@Test
	@DisplayName("R-09: a null unit means the quantity is already in the base unit")
	void nullUnitIsAlreadyBase() {
		assertThat(UnitConversionPolicy.toBaseUnit(PRODUCT, null, new BigDecimal("7"), null).value())
				.isEqualByComparingTo("7.0000");
	}

	@Test
	@DisplayName("R-09: an alternative unit is multiplied by the conversion factor at scale 4")
	void alternativeUnitMultipliedByFactor() {
		assertThat(UnitConversionPolicy.toBaseUnit(PRODUCT, UNIT, new BigDecimal("3"), new BigDecimal("2.5")).value())
				.isEqualByComparingTo("7.5000");
	}

	@Test
	@DisplayName("R-09: an unconvertible unit (no factor) is refused")
	void missingFactorRefused() {
		assertThatThrownBy(() -> UnitConversionPolicy.toBaseUnit(PRODUCT, UNIT, new BigDecimal("3"), null))
				.isInstanceOf(UnitConversionUnavailableException.class);
	}

	@Test
	@DisplayName("R-09: a non-positive conversion factor is refused")
	void nonPositiveFactorRefused() {
		assertThatThrownBy(() -> UnitConversionPolicy.toBaseUnit(PRODUCT, UNIT, new BigDecimal("3"), BigDecimal.ZERO))
				.isInstanceOf(UnitConversionUnavailableException.class);
	}
}
