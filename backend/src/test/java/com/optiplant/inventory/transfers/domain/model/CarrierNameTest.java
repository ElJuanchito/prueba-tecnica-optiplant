package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link CarrierName} — trimmed, non-blank, {@code <= 100} (design §3.1, R-10). */
class CarrierNameTest {

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new CarrierName("  DHL  ").value()).isEqualTo("DHL");
	}

	@Test
	void rejectsNullOrBlank() {
		assertThatThrownBy(() -> new CarrierName(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CarrierName("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMoreThanOneHundredCharacters() {
		assertThatThrownBy(() -> new CarrierName("x".repeat(101))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsExactlyOneHundredCharacters() {
		assertThat(new CarrierName("x".repeat(100)).value()).hasSize(100);
	}
}
