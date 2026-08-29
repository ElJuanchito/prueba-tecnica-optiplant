package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SettledQuantity} — non-negative, scale 4 (design §3.1). Zero MUST be
 * legal: R-19 makes a full-loss receipt (zero received) valid, not an error.
 */
class SettledQuantityTest {

	@Test
	void normalizesToScaleFour() {
		assertThat(new SettledQuantity(new BigDecimal("8.1")).value()).isEqualByComparingTo("8.1000");
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new SettledQuantity(null)).isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void acceptsZero() {
		assertThat(new SettledQuantity(BigDecimal.ZERO).value()).isEqualByComparingTo("0.0000");
		assertThat(SettledQuantity.zero().value()).isEqualByComparingTo("0.0000");
	}

	@Test
	void rejectsNegative() {
		assertThatThrownBy(() -> new SettledQuantity(new BigDecimal("-0.0001")))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}
}
