package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferNumber} — {@code TRF-<yyyy>-<nnnn>} (design §3.1, §6.2, D-3),
 * matching the seeded {@code TRF-2026-0001}.
 */
class TransferNumberTest {

	@Test
	void acceptsTheSeededShape() {
		assertThat(new TransferNumber("TRF-2026-0001").value()).isEqualTo("TRF-2026-0001");
	}

	@Test
	void acceptsASuffixWiderThanFourDigits() {
		// D-3: the suffix widens past 9999 rather than truncating.
		assertThat(new TransferNumber("TRF-2026-12345").value()).isEqualTo("TRF-2026-12345");
	}

	@Test
	void rejectsNullOrBlank() {
		assertThatThrownBy(() -> new TransferNumber(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TransferNumber("  ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAWrongShape() {
		assertThatThrownBy(() -> new TransferNumber("TRF-26-0001")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TransferNumber("ABC-2026-0001")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TransferNumber("TRF-2026-1")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAValueLongerThanFiftyCharacters() {
		String tooLong = "TRF-2026-" + "1".repeat(45);
		assertThatThrownBy(() -> new TransferNumber(tooLong)).isInstanceOf(IllegalArgumentException.class);
	}
}
