package com.optiplant.inventory.transfers.domain.service;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException;
import com.optiplant.inventory.transfers.domain.model.ReceiptLine;
import com.optiplant.inventory.transfers.domain.model.ReceiptOutcome;
import com.optiplant.inventory.transfers.domain.model.SettledQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per dispatched item: {@code 0 <= received <= dispatched} — above is refused (F-4/PA-03);
 * {@code discrepancy = dispatched - received}, so RN-06 holds by construction and never by a
 * second client-supplied input (design §3.3). A non-blank reason is mandatory whenever
 * {@code discrepancy > 0} (R-18). Zero received on every item is still a valid receipt — total
 * loss is a 100% discrepancy, not an error (R-19).
 */
public final class TransferReceiptPolicy {

	private TransferReceiptPolicy() {
	}

	public static ReceiptOutcome apply(List<TransferItem> dispatchedItems, List<ReceiptLineCommand> lines) {
		if (lines == null || lines.size() != dispatchedItems.size()) {
			throw new InvalidTransferQuantityException("every dispatched item must be named exactly once at receipt");
		}
		Map<UUID, ReceiptLineCommand> byItem = new HashMap<>();
		for (ReceiptLineCommand line : lines) {
			if (byItem.put(line.itemExternalId(), line) != null) {
				throw new DuplicateTransferItemException();
			}
		}
		List<ReceiptLine> settled = new ArrayList<>();
		boolean anyDiscrepancy = false;
		for (TransferItem item : dispatchedItems) {
			ReceiptLineCommand line = byItem.get(item.externalId());
			if (line == null) {
				throw new TransferItemNotFoundException(item.externalId());
			}
			BigDecimal dispatched = item.dispatchedQuantity().value();
			BigDecimal received = line.receivedQuantity();
			if (received == null || received.signum() < 0 || received.compareTo(dispatched) > 0) {
				throw new InvalidTransferQuantityException(
						"received quantity must be between 0 and the dispatched quantity of " + dispatched);
			}
			BigDecimal discrepancy = dispatched.subtract(received);
			String reason = null;
			if (discrepancy.signum() > 0) {
				anyDiscrepancy = true;
				if (line.discrepancyReason() == null || line.discrepancyReason().isBlank()) {
					throw new TransferReasonRequiredException();
				}
				reason = line.discrepancyReason();
			}
			settled.add(new ReceiptLine(item.externalId(), item.productExternalId(), new SettledQuantity(received),
					new SettledQuantity(discrepancy), reason));
		}
		TransferStatus status = anyDiscrepancy ? TransferStatus.RECEIVED_WITH_DISCREPANCY : TransferStatus.RECEIVED;
		return new ReceiptOutcome(settled, status, anyDiscrepancy);
	}

	/** The client-supplied receipt line — a raw {@code (item, receivedQuantity, discrepancyReason)} triple. */
	public record ReceiptLineCommand(UUID itemExternalId, BigDecimal receivedQuantity, String discrepancyReason) {
	}
}
