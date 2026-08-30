package com.optiplant.inventory.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleNotesTest {

	@Test
	@DisplayName("F-3: Round trip parse and render of notes with VOID_REASON token and human note")
	void roundTripTokenAndHumanNote() {
		String raw = "VOID_REASON:Customer returned damaged goods\nCashier observed box was dented";

		SaleNotes parsed = SaleNotes.parse(raw);

		assertThat(parsed.cancellationReason()).isNotNull();
		assertThat(parsed.cancellationReason().value()).isEqualTo("Customer returned damaged goods");
		assertThat(parsed.humanNote()).isEqualTo("Cashier observed box was dented");

		String rendered = parsed.render();
		assertThat(rendered).isEqualTo(raw);
	}

	@Test
	@DisplayName("F-3: Notes without VOID_REASON token parse safely as human note with no cancellation reason")
	void notesWithoutTokenParseAsHumanNote() {
		String raw = "Free prose notes entered by cashier during checkout";

		SaleNotes parsed = SaleNotes.parse(raw);

		assertThat(parsed.cancellationReason()).isNull();
		assertThat(parsed.humanNote()).isEqualTo(raw);
		assertThat(parsed.render()).isEqualTo(raw);
	}

	@Test
	@DisplayName("F-3: Null and blank notes parse to empty notes")
	void nullOrBlankNotesParseToEmpty() {
		SaleNotes nullParsed = SaleNotes.parse(null);
		assertThat(nullParsed.cancellationReason()).isNull();
		assertThat(nullParsed.humanNote()).isNull();
		assertThat(nullParsed.render()).isNull();

		SaleNotes blankParsed = SaleNotes.parse("   ");
		assertThat(blankParsed.cancellationReason()).isNull();
		assertThat(blankParsed.humanNote()).isNull();
	}

	@Test
	@DisplayName("F-3: Appending cancellation reason to existing human note renders token first")
	void appendingReasonRendersTokenFirst() {
		SaleNotes initial = SaleNotes.fromHumanNote("Customer requested gift wrap");
		SaleNotes cancelled = initial.withCancellationReason(CancellationReason.of("Defective product"));

		assertThat(cancelled.cancellationReason().value()).isEqualTo("Defective product");
		assertThat(cancelled.humanNote()).isEqualTo("Customer requested gift wrap");

		String expectedRender = "VOID_REASON:Defective product\nCustomer requested gift wrap";
		assertThat(cancelled.render()).isEqualTo(expectedRender);
	}
}
