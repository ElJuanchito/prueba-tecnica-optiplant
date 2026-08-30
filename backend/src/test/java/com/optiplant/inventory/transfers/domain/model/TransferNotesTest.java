package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferNotes} — the sole reader and writer of the F-1 priority token
 * (design §3.5).
 */
class TransferNotesTest {

	@Test
	void rendersAndParsesBackTheSameNotes() {
		TransferNotes original = TransferNotes.empty(TransferPriority.URGENT).withObservation("first observation")
				.withObservation("second observation");

		TransferNotes roundTripped = TransferNotes.parse(original.render());

		assertThat(roundTripped.priority()).isEqualTo(TransferPriority.URGENT);
		assertThat(roundTripped.observations()).containsExactly("first observation", "second observation");
	}

	@Test
	void notesWithNoPriorityTokenDefaultToStandardWithTheWholeTextAsObservations() {
		// 02-seed-data.sql:192 seeds a transfer whose notes are free prose; the parser MUST NOT throw.
		TransferNotes parsed = TransferNotes.parse("Free-form prose with no token at all");

		assertThat(parsed.priority()).isEqualTo(TransferPriority.STANDARD);
		assertThat(parsed.observations()).containsExactly("Free-form prose with no token at all");
	}

	@Test
	void blankOrNullRawParsesToStandardWithNoObservations() {
		assertThat(TransferNotes.parse(null).priority()).isEqualTo(TransferPriority.STANDARD);
		assertThat(TransferNotes.parse(null).observations()).isEmpty();
		assertThat(TransferNotes.parse("   ").observations()).isEmpty();
	}

	@Test
	void theTokenNeverAppearsInObservations() {
		TransferNotes notes = TransferNotes.parse(TransferNotes.empty(TransferPriority.LOW)
				.withObservation("carrier delayed").render());

		assertThat(notes.observations()).noneMatch(observation -> observation.contains("PRIORITY:"));
	}

	@Test
	void anUnrecognizedPriorityTokenFallsBackToStandard() {
		TransferNotes parsed = TransferNotes.parse("PRIORITY:NOT_A_LEVEL\nsome text");

		assertThat(parsed.priority()).isEqualTo(TransferPriority.STANDARD);
	}

	@Test
	void nullPriorityDefaultsToStandardAndNullObservationsBecomeEmpty() {
		TransferNotes notes = new TransferNotes(null, null);

		assertThat(notes.priority()).isEqualTo(TransferPriority.STANDARD);
		assertThat(notes.observations()).isEmpty();
	}

	@Test
	void withObservationAppendsWithoutMutatingTheOriginal() {
		TransferNotes original = TransferNotes.empty(TransferPriority.STANDARD);

		TransferNotes withOne = original.withObservation("one");

		assertThat(original.observations()).isEmpty();
		assertThat(withOne.observations()).containsExactly("one");
	}

	@Test
	void renderWritesThePriorityTokenAsTheFirstLine() {
		String rendered = TransferNotes.empty(TransferPriority.URGENT).withObservation("obs").render();

		assertThat(rendered).isEqualTo("PRIORITY:URGENT\nobs");
	}

	@Test
	void constructorCopiesTheObservationListDefensively() {
		List<String> mutable = new java.util.ArrayList<>(List.of("a"));
		TransferNotes notes = new TransferNotes(TransferPriority.STANDARD, mutable);
		mutable.add("b");

		assertThat(notes.observations()).containsExactly("a");
	}
}
