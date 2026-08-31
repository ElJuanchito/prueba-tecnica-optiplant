package com.optiplant.inventory.logistics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RouteSort#parse(String)} (RF-LOG-03): only {@code cost_asc}, {@code
 * duration_asc} and {@code priority_desc} are accepted; anything else — including {@code null}
 * and the empty string — is an {@link IllegalArgumentException}, so the web layer answers
 * {@code 400 invalid_request} rather than letting an arbitrary token reach the query.
 */
class RouteSortTest {

	@Test
	void parsesCostAscToCostAsc() {
		assertThat(RouteSort.parse("cost_asc")).isEqualTo(RouteSort.COST_ASC);
	}

	@Test
	void parsesDurationAscToDurationAsc() {
		assertThat(RouteSort.parse("duration_asc")).isEqualTo(RouteSort.DURATION_ASC);
	}

	@Test
	void parsesPriorityDescToPriorityDesc() {
		assertThat(RouteSort.parse("priority_desc")).isEqualTo(RouteSort.PRIORITY_DESC);
	}

	@Test
	void rejectsAnUnknownToken() {
		assertThatThrownBy(() -> RouteSort.parse("cost_desc")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTheEmptyString() {
		assertThatThrownBy(() -> RouteSort.parse("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> RouteSort.parse(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void isCaseSensitiveByDesign() {
		assertThatThrownBy(() -> RouteSort.parse("COST_ASC")).isInstanceOf(IllegalArgumentException.class);
	}
}
