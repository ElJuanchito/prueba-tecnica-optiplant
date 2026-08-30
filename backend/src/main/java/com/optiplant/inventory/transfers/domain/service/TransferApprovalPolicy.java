package com.optiplant.inventory.transfers.domain.service;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.model.ApprovedLine;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per line: approved {@code > 0} and {@code <= requested}; every item appears exactly once
 * (R-07, F-2, design §3.3). Approval overwrites {@code requestedQuantity} with the agreed value
 * (PA-02) and returns the observation lines describing each reduction, appended to
 * {@code TransferNotes} by {@link com.optiplant.inventory.transfers.domain.model.Transfer#approve}.
 */
public final class TransferApprovalPolicy {

	private TransferApprovalPolicy() {
	}

	public static ApprovalOutcome apply(List<TransferItem> items, List<ApprovedLine> lines) {
		if (lines == null || lines.size() != items.size()) {
			throw new InvalidTransferQuantityException("every item must be named exactly once in the approval");
		}
		Map<java.util.UUID, BigDecimal> byItem = new HashMap<>();
		for (ApprovedLine line : lines) {
			if (byItem.put(line.itemExternalId(), line.approvedQuantity()) != null) {
				throw new DuplicateTransferItemException();
			}
		}
		List<TransferItem> adjusted = new ArrayList<>();
		List<String> observations = new ArrayList<>();
		for (TransferItem item : items) {
			BigDecimal approved = byItem.get(item.externalId());
			if (approved == null) {
				throw new TransferItemNotFoundException(item.externalId());
			}
			BigDecimal requested = item.requestedQuantity().value();
			if (approved.signum() <= 0 || approved.compareTo(requested) > 0) {
				throw new InvalidTransferQuantityException(
						"approved quantity must be > 0 and <= the requested quantity of " + requested);
			}
			if (approved.compareTo(requested) < 0) {
				observations.add("Item " + item.productExternalId() + " approved at " + approved
						+ " instead of the requested " + requested);
			}
			adjusted.add(new TransferItem(item.externalId(), item.productExternalId(), new TransferQuantity(approved),
					item.dispatchedQuantity(), item.receivedQuantity(), item.discrepancyQuantity(),
					item.discrepancyReason()));
		}
		return new ApprovalOutcome(adjusted, observations);
	}

	/** {@code items} carries the agreed (post-approval) quantities; {@code observations} document every reduction. */
	public record ApprovalOutcome(List<TransferItem> items, List<String> observations) {
	}
}
