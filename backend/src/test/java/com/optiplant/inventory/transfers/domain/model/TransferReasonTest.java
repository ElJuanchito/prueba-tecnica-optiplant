package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferReason} — the mandatory, non-blank reason for rejection (R-09), a
 * receipt discrepancy (R-18) or a cancellation (R-21), design §3.1.
 */
class TransferReasonTest {

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new TransferReason("  damaged in transit  ").value()).isEqualTo("damaged in transit");
	}

	@Test
	void rejectsNullOrBlankWithItsOwnErrorCode() {
		assertThatThrownBy(() -> new TransferReason(null)).isInstanceOf(TransferReasonRequiredException.class);
		assertThatThrownBy(() -> new TransferReason("   ")).isInstanceOf(TransferReasonRequiredException.class);
	}

	@Test
	void rejectsMoreThanFiveHundredCharacters() {
		assertThatThrownBy(() -> new TransferReason("x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsExactlyFiveHundredCharacters() {
		assertThat(new TransferReason("x".repeat(500)).value()).hasSize(500);
	}
}
