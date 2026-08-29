package com.optiplant.inventory.transfers.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.model.ApprovedLine;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.service.TransferApprovalPolicy.ApprovalOutcome;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferApprovalPolicy} — R-07: approved {@code > 0} and
 * {@code <= requested}; every item appears exactly once (F-2, design §3.3).
 */
class TransferApprovalPolicyTest {

	private static TransferItem requested(UUID itemId, String quantity) {
		return TransferItem.requested(itemId, UUID.randomUUID(), new TransferQuantity(new BigDecimal(quantity)));
	}

	@Test
	void reducingOneHundredToSixtyIsAllowedAndDocumented() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		ApprovalOutcome outcome = TransferApprovalPolicy.apply(List.of(item), List.of(new ApprovedLine(itemId, new BigDecimal("60"))));

		assertThat(outcome.items()).hasSize(1);
		assertThat(outcome.items().get(0).requestedQuantity().value()).isEqualByComparingTo("60.0000");
		assertThat(outcome.observations()).hasSize(1);
	}

	@Test
	void approvingTheFullRequestedAmountAddsNoObservation() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		ApprovalOutcome outcome = TransferApprovalPolicy.apply(List.of(item), List.of(new ApprovedLine(itemId, new BigDecimal("100"))));

		assertThat(outcome.observations()).isEmpty();
	}

	@Test
	void approvingAboveTheRequestedAmountIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item),
				List.of(new ApprovedLine(itemId, new BigDecimal("120")))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void approvingZeroIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item),
				List.of(new ApprovedLine(itemId, BigDecimal.ZERO))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void approvingANegativeQuantityIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item),
				List.of(new ApprovedLine(itemId, new BigDecimal("-1")))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void everyItemMustAppearExactlyOnce() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item), List.of()))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void namingTheSameItemTwiceIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, "100");
		// A second, unrelated item keeps the sizes equal so only the duplicate-item rule fires.
		TransferItem other = requested(UUID.randomUUID(), "10");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item, other),
				List.of(new ApprovedLine(itemId, new BigDecimal("50")), new ApprovedLine(itemId, new BigDecimal("50")))))
				.isInstanceOf(DuplicateTransferItemException.class);
	}

	@Test
	void anUnknownItemIsRefused() {
		TransferItem item = requested(UUID.randomUUID(), "100");

		assertThatThrownBy(() -> TransferApprovalPolicy.apply(List.of(item),
				List.of(new ApprovedLine(UUID.randomUUID(), new BigDecimal("50")))))
				.isInstanceOf(TransferItemNotFoundException.class);
	}
}
