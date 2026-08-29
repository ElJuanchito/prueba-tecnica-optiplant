package com.optiplant.inventory.transfers.domain.model;

import java.util.List;

/**
 * The resolved outcome of a full or partial receipt (CU-TRA-04, CU-TRA-05, R-17/R-18), produced
 * by {@code TransferReceiptPolicy} and applied by {@link Transfer#receive}. {@code status} is
 * {@link TransferStatus#RECEIVED} when every line's discrepancy is zero,
 * {@link TransferStatus#RECEIVED_WITH_DISCREPANCY} otherwise — one comparison in the middle of
 * one physical act (contract §2.4), never two endpoints picking the outcome.
 */
public record ReceiptOutcome(List<ReceiptLine> lines, TransferStatus status, boolean hasDiscrepancy) {

	public ReceiptOutcome {
		lines = List.copyOf(lines);
	}
}
