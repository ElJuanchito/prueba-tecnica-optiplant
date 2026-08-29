package com.optiplant.inventory.notifications.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.shared.alert.AlertType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AlertDedupKey} — F-1 token shape, ≤150 characters (R-21). */
class AlertDedupKeyTest {

	@Test
	void titleIsAlertTypeColonSubjectToken() {
		UUID productId = UUID.randomUUID();
		AlertDedupKey key = new AlertDedupKey(UUID.randomUUID(), AlertType.STOCK_MINIMUM, productId.toString());

		assertThat(key.title()).isEqualTo("STOCK_MINIMUM:" + productId);
	}

	@Test
	void titleNeverExceedsOneHundredFiftyCharacters() {
		UUID productId = UUID.randomUUID();
		AlertDedupKey key = new AlertDedupKey(UUID.randomUUID(), AlertType.STOCK_MINIMUM, productId.toString());

		assertThat(key.title().length()).isLessThanOrEqualTo(150);
	}

	@Test
	void anOverLongSubjectTokenIsRejected() {
		AlertDedupKey key = new AlertDedupKey(UUID.randomUUID(), AlertType.STOCK_MINIMUM, "x".repeat(200));

		assertThatThrownBy(key::title).isInstanceOf(IllegalArgumentException.class);
	}
}
