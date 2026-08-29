package com.optiplant.inventory.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link StockLevel} — non-negative, scale 4 (design §3.1). */
class StockLevelTest {

	@Test
	void acceptsZero() {
		assertThat(new StockLevel(BigDecimal.ZERO).value()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void normalizesToScaleFour() {
		assertThat(new StockLevel(new BigDecimal("100")).value()).isEqualTo(new BigDecimal("100.0000"));
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new StockLevel(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNegative() {
		assertThatThrownBy(() -> new StockLevel(new BigDecimal("-0.0001")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void zeroFactoryReturnsZero() {
		assertThat(StockLevel.zero().value()).isEqualByComparingTo(BigDecimal.ZERO);
	}
}
