package com.optiplant.inventory.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnitCode} (R-07, R-13): trim + upper-case normalization,
 * the {@code ^[A-Z0-9_]+$} character set (which rejects whitespace inside the
 * value), and the two distinct bounds — {@code 1..50} for the canonical
 * constructor, {@code 1..20} for {@link UnitCode#baseUnit(String)}.
 */
class UnitCodeTest {

	@Test
	void lowerCaseInputIsUpperCased() {
		assertThat(new UnitCode("kg").value()).isEqualTo("KG");
	}

	@Test
	void trimsSurroundingWhitespace() {
		assertThat(new UnitCode("  bolsa_80k_sem  ").value()).isEqualTo("BOLSA_80K_SEM");
	}

	@Test
	void rejectsAValueWithWhitespaceInside() {
		// "Saco de 50" normalizes to "SACO DE 50"; the spaces are not in ^[A-Z0-9_]+$.
		assertThatThrownBy(() -> new UnitCode("Saco de 50")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAValueWithPunctuationOutsideTheCharacterSet() {
		assertThatThrownBy(() -> new UnitCode("KG-2")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new UnitCode(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsABlankValue() {
		assertThatThrownBy(() -> new UnitCode("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theCanonicalConstructorAcceptsUpToFiftyCharacters() {
		assertThat(new UnitCode("A".repeat(50)).value()).hasSize(50);
	}

	@Test
	void theCanonicalConstructorRejectsFiftyOneCharacters() {
		assertThatThrownBy(() -> new UnitCode("A".repeat(51))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void baseUnitNormalizesLikeTheCanonicalConstructor() {
		assertThat(UnitCode.baseUnit("  litro  ").value()).isEqualTo("LITRO");
	}

	@Test
	void baseUnitAcceptsUpToTwentyCharacters() {
		assertThat(UnitCode.baseUnit("A".repeat(20)).value()).hasSize(20);
	}

	@Test
	void baseUnitRejectsTwentyOneCharactersEvenThoughTheCanonicalConstructorAllowsThem() {
		String twentyOne = "A".repeat(21);

		assertThat(new UnitCode(twentyOne).value()).hasSize(21);
		assertThatThrownBy(() -> UnitCode.baseUnit(twentyOne)).isInstanceOf(IllegalArgumentException.class);
	}
}
