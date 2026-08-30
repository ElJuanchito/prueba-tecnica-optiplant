package com.optiplant.inventory.transfers.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException;
import com.optiplant.inventory.transfers.domain.model.ReceiptOutcome;
import com.optiplant.inventory.transfers.domain.model.SettledQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.service.TransferReceiptPolicy.ReceiptLineCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferReceiptPolicy} — {@code 0 <= received <= dispatched} (F-4/PA-03);
 * {@code discrepancy = dispatched - received} always (RN-06); a reason is mandatory only on a
 * shortfall (R-18); an all-zero receipt is still valid (R-19).
 */
class TransferReceiptPolicyTest {

	private static TransferItem dispatched(UUID itemId, String requested, String dispatchedQuantity) {
		TransferItem item = TransferItem.requested(itemId, UUID.randomUUID(), new TransferQuantity(new BigDecimal(requested)));
		return new TransferItem(item.externalId(), item.productExternalId(), item.requestedQuantity(),
				new SettledQuantity(new BigDecimal(dispatchedQuantity)), item.receivedQuantity(),
				item.discrepancyQuantity(), null);
	}

	@Test
	void aShortfallOfTenComputesTheDiscrepancyByConstruction() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		ReceiptOutcome outcome = TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("90"), "damaged in transit")));

		assertThat(outcome.status()).isEqualTo(TransferStatus.RECEIVED_WITH_DISCREPANCY);
		assertThat(outcome.hasDiscrepancy()).isTrue();
		assertThat(outcome.lines().get(0).receivedQuantity().value()).isEqualByComparingTo("90.0000");
		assertThat(outcome.lines().get(0).discrepancyQuantity().value()).isEqualByComparingTo("10.0000");
	}

	@Test
	void aFullReceiptResolvesToReceivedWithNoAlertMaterial() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		ReceiptOutcome outcome = TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("100"), null)));

		assertThat(outcome.status()).isEqualTo(TransferStatus.RECEIVED);
		assertThat(outcome.hasDiscrepancy()).isFalse();
		assertThat(outcome.lines().get(0).discrepancyQuantity().value()).isEqualByComparingTo("0.0000");
	}

	@Test
	void aShortfallWithNoReasonIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("90"), null))))
				.isInstanceOf(TransferReasonRequiredException.class);
		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("90"), "  "))))
				.isInstanceOf(TransferReasonRequiredException.class);
	}

	@Test
	void zeroReceivedOnEveryItemIsAValidTotalLossReceipt() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		ReceiptOutcome outcome = TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, BigDecimal.ZERO, "total loss")));

		assertThat(outcome.status()).isEqualTo(TransferStatus.RECEIVED_WITH_DISCREPANCY);
		assertThat(outcome.lines().get(0).discrepancyQuantity().value()).isEqualByComparingTo("100.0000");
	}

	@Test
	void receivingAboveTheDispatchedQuantityIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("101"), null))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void receivingANegativeQuantityIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("-1"), null))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void aNullReceivedQuantityIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(itemId, null, null))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void everyDispatchedItemMustBeNamedExactlyOnce() {
		TransferItem item = dispatched(UUID.randomUUID(), "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item), List.of()))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void namingTheSameItemTwiceIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = dispatched(itemId, "100", "100");
		TransferItem other = dispatched(UUID.randomUUID(), "10", "10");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item, other),
				List.of(new ReceiptLineCommand(itemId, new BigDecimal("50"), null),
						new ReceiptLineCommand(itemId, new BigDecimal("50"), null))))
				.isInstanceOf(DuplicateTransferItemException.class);
	}

	@Test
	void anUnknownItemIsRefused() {
		TransferItem item = dispatched(UUID.randomUUID(), "100", "100");

		assertThatThrownBy(() -> TransferReceiptPolicy.apply(List.of(item),
				List.of(new ReceiptLineCommand(UUID.randomUUID(), new BigDecimal("50"), null))))
				.isInstanceOf(TransferItemNotFoundException.class);
	}
}
