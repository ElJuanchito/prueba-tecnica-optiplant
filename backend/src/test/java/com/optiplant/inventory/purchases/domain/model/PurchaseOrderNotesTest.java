package com.optiplant.inventory.purchases.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.CancellationReasonRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseOrderNotesTest {

	@Test
	@DisplayName("F-3: the CANCEL_REASON token round-trips through render/parse")
	void tokenRoundTrip() {
		PurchaseOrderNotes notes = PurchaseOrderNotes.fromHumanNote("call the warehouse")
				.withCancellationReason("supplier out of stock");

		String rendered = notes.render();
		assertThat(rendered).isEqualTo("CANCEL_REASON:supplier out of stock\ncall the warehouse");

		PurchaseOrderNotes parsed = PurchaseOrderNotes.parse(rendered);
		assertThat(parsed.cancellationReason()).isEqualTo("supplier out of stock");
		assertThat(parsed.humanNote()).isEqualTo("call the warehouse");
	}

	@Test
	@DisplayName("F-3: a reason with no human note renders as the bare token and parses back")
	void reasonOnlyRoundTrip() {
		PurchaseOrderNotes notes = PurchaseOrderNotes.empty().withCancellationReason("duplicate order");

		assertThat(notes.render()).isEqualTo("CANCEL_REASON:duplicate order");

		PurchaseOrderNotes parsed = PurchaseOrderNotes.parse(notes.render());
		assertThat(parsed.cancellationReason()).isEqualTo("duplicate order");
		assertThat(parsed.humanNote()).isNull();
	}

	@Test
	@DisplayName("D-8: free prose with no token parses to no reason and the whole text as the human note")
	void missingTokenDoesNotThrow() {
		PurchaseOrderNotes parsed = PurchaseOrderNotes.parse("just a plain observation from the buyer");

		assertThat(parsed.cancellationReason()).isNull();
		assertThat(parsed.humanNote()).isEqualTo("just a plain observation from the buyer");
	}

	@Test
	@DisplayName("D-8: a null or blank note parses to empty")
	void nullOrBlankParsesToEmpty() {
		assertThat(PurchaseOrderNotes.parse(null)).isEqualTo(PurchaseOrderNotes.empty());
		assertThat(PurchaseOrderNotes.parse("   ")).isEqualTo(PurchaseOrderNotes.empty());
	}

	@Test
	@DisplayName("F-3: the token never appears in the exposed human note")
	void tokenAbsentFromExposedNote() {
		PurchaseOrderNotes parsed = PurchaseOrderNotes.parse("CANCEL_REASON:wrong supplier\nplease re-issue");

		assertThat(parsed.humanNote()).doesNotContain("CANCEL_REASON");
		assertThat(parsed.humanNote()).isEqualTo("please re-issue");
	}

	@Test
	@DisplayName("R-13: a blank cancellation reason is refused")
	void blankReasonRefused() {
		assertThatThrownBy(() -> PurchaseOrderNotes.empty().withCancellationReason("  "))
				.isInstanceOf(CancellationReasonRequiredException.class);
		assertThatThrownBy(() -> PurchaseOrderNotes.empty().withCancellationReason(null))
				.isInstanceOf(CancellationReasonRequiredException.class);
	}
}
