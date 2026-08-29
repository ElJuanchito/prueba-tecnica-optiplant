package com.optiplant.inventory.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Sku} (R-06): trim + upper-case normalization so
 * {@code abc-1} and {@code ABC-1} are the same article, and the 50/51-character
 * boundary against {@code products.sku VARCHAR(50)}.
 */
class SkuTest {

	@Test
	void lowercaseAndUppercaseInputProduceTheSameValue() {
		assertThat(new Sku("abc-1").value()).isEqualTo(new Sku("ABC-1").value());
		assertThat(new Sku("abc-1").value()).isEqualTo("ABC-1");
	}

	@Test
	void upperCasesMixedCaseInput() {
		assertThat(new Sku("Fert-Npk-151515").value()).isEqualTo("FERT-NPK-151515");
	}

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new Sku("  fert-npk-151515  ").value()).isEqualTo("FERT-NPK-151515");
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new Sku(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAnEmptyValue() {
		assertThatThrownBy(() -> new Sku("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAWhitespaceOnlyValue() {
		assertThatThrownBy(() -> new Sku("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsExactlyFiftyCharacters() {
		assertThat(new Sku("a".repeat(50)).value()).hasSize(50);
	}

	@Test
	void acceptsFiftyCharactersAfterTrimming() {
		assertThat(new Sku("  " + "a".repeat(50) + "  ").value()).hasSize(50);
	}

	@Test
	void rejectsFiftyOneCharacters() {
		assertThatThrownBy(() -> new Sku("a".repeat(51))).isInstanceOf(IllegalArgumentException.class);
	}
}
