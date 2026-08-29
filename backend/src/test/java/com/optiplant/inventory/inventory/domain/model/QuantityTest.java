package com.optiplant.inventory.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link Quantity} — strictly positive, scale 4 (design §3.1). */
class QuantityTest {

	@Test
	void normalizesToScaleFour() {
		assertThat(new Quantity(new BigDecimal("8")).value()).isEqualByComparingTo("8.0000");
		assertThat(new Quantity(new BigDecimal("8.12345")).value()).isEqualTo(new BigDecimal("8.1235"));
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new Quantity(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsZero() {
		assertThatThrownBy(() -> new Quantity(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNegative() {
		assertThatThrownBy(() -> new Quantity(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
	}
}
