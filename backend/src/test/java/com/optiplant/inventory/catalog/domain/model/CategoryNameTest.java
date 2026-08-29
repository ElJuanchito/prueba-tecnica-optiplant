package com.optiplant.inventory.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CategoryName} (R-02): trimming, blank rejection, the
 * 100/101-character boundary, and the case-insensitive comparison key.
 */
class CategoryNameTest {

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new CategoryName("  Fertilizantes  ").value()).isEqualTo("Fertilizantes");
	}

	@Test
	void preservesCaseInTheStoredValue() {
		assertThat(new CategoryName("Fertilizantes y Nutrición Vegetal").value())
				.isEqualTo("Fertilizantes y Nutrición Vegetal");
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new CategoryName(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAnEmptyName() {
		assertThatThrownBy(() -> new CategoryName("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAWhitespaceOnlyName() {
		assertThatThrownBy(() -> new CategoryName("    ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsExactlyOneHundredCharacters() {
		String name = "a".repeat(100);

		assertThat(new CategoryName(name).value()).hasSize(100);
	}

	@Test
	void acceptsOneHundredCharactersAfterTrimming() {
		String name = "  " + "a".repeat(100) + "  ";

		assertThat(new CategoryName(name).value()).hasSize(100);
	}

	@Test
	void rejectsOneHundredAndOneCharacters() {
		String name = "a".repeat(101);

		assertThatThrownBy(() -> new CategoryName(name)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void comparisonKeyIsCaseInsensitive() {
		CategoryName mixed = new CategoryName("Fertilizantes");
		CategoryName upper = new CategoryName("FERTILIZANTES");

		assertThat(mixed.comparisonKey()).isEqualTo(upper.comparisonKey());
		assertThat(mixed.comparisonKey()).isEqualTo("fertilizantes");
	}

	@Test
	void comparisonKeyIgnoresSurroundingWhitespaceToo() {
		assertThat(new CategoryName("  Semillas  ").comparisonKey()).isEqualTo("semillas");
	}
}
