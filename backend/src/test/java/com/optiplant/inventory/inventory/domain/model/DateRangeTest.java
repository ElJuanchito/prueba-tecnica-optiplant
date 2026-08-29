package com.optiplant.inventory.inventory.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link DateRange} — both bounds nullable, {@code from <= to} (R-16). */
class DateRangeTest {

	private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

	@Test
	void bothBoundsMayBeAbsent() {
		DateRange range = new DateRange(null, null);
		assertThat(range.from()).isNull();
		assertThat(range.to()).isNull();
	}

	@Test
	void acceptsFromBeforeTo() {
		DateRange range = new DateRange(EARLIER, LATER);
		assertThat(range.from()).isEqualTo(EARLIER);
		assertThat(range.to()).isEqualTo(LATER);
	}

	@Test
	void acceptsFromEqualToTo() {
		DateRange range = new DateRange(EARLIER, EARLIER);
		assertThat(range.from()).isEqualTo(range.to());
	}

	@Test
	void rejectsFromAfterTo() {
		assertThatThrownBy(() -> new DateRange(LATER, EARLIER)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aSingleAbsentBoundIsNotMalformed() {
		assertThat(new DateRange(EARLIER, null).to()).isNull();
		assertThat(new DateRange(null, LATER).from()).isNull();
	}
}
