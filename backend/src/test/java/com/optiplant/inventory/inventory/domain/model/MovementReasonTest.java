package com.optiplant.inventory.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link MovementReason} — mandatory, non-blank, trimmed, ≤500 chars (RN-11, R-07). */
class MovementReasonTest {

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new MovementReason("  broken bag  ").value()).isEqualTo("broken bag");
	}

	@Test
	void rejectsNullWithItsOwnErrorCode() {
		assertThatThrownBy(() -> new MovementReason(null)).isInstanceOf(AdjustmentReasonRequiredException.class);
	}

	@Test
	void rejectsBlankWithItsOwnErrorCode() {
		assertThatThrownBy(() -> new MovementReason("   ")).isInstanceOf(AdjustmentReasonRequiredException.class);
	}

	@Test
	void rejectsOverLongReason() {
		String tooLong = "x".repeat(501);
		assertThatThrownBy(() -> new MovementReason(tooLong)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsExactlyFiveHundredCharacters() {
		String exact = "x".repeat(500);
		assertThat(new MovementReason(exact).value()).hasSize(500);
	}
}
