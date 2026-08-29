package com.optiplant.inventory.catalog.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ActiveFilter#parse(String)} (R-12): only {@code true},
 * {@code false} and {@code all} are accepted; anything else — including {@code
 * null} and the empty string — is an {@link IllegalArgumentException}, so the web
 * layer answers {@code 400} rather than a type-mismatch page.
 */
class ActiveFilterTest {

	@Test
	void parsesTrueToActive() {
		assertThat(ActiveFilter.parse("true")).isEqualTo(ActiveFilter.ACTIVE);
	}

	@Test
	void parsesFalseToInactive() {
		assertThat(ActiveFilter.parse("false")).isEqualTo(ActiveFilter.INACTIVE);
	}

	@Test
	void parsesAllToAll() {
		assertThat(ActiveFilter.parse("all")).isEqualTo(ActiveFilter.ALL);
	}

	@Test
	void rejectsAnUnknownToken() {
		assertThatThrownBy(() -> ActiveFilter.parse("maybe")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTheEmptyString() {
		assertThatThrownBy(() -> ActiveFilter.parse("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> ActiveFilter.parse(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void isCaseSensitiveByDesign() {
		assertThatThrownBy(() -> ActiveFilter.parse("TRUE")).isInstanceOf(IllegalArgumentException.class);
	}
}
