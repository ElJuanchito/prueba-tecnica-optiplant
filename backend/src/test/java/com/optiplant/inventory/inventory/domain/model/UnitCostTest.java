package com.optiplant.inventory.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link UnitCost} — non-negative, scale 4 (design §3.1). */
class UnitCostTest {

	@Test
	void acceptsZero() {
		assertThat(new UnitCost(BigDecimal.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void normalizesToScaleFour() {
		assertThat(new UnitCost(new BigDecimal("12.5")).value()).isEqualTo(new BigDecimal("12.5000"));
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new UnitCost(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNegative() {
		assertThatThrownBy(() -> new UnitCost(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
	}
}
