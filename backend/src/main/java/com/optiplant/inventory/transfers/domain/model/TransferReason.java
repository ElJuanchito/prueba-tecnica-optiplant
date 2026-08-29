package com.optiplant.inventory.transfers.domain.model;

import com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException;

/**
 * A mandatory, non-blank reason for rejection (R-09), a receipt discrepancy (R-18) or a
 * cancellation (R-21) — design §3.1. Trimmed and bounded at 500 characters.
 *
 * <p>A {@code null} or blank value throws {@link TransferReasonRequiredException} so those rules
 * keep {@code transfer_reason_required}, never the generic {@code invalid_request}.
 */
public record TransferReason(String value) {

	private static final int MAX_LENGTH = 500;

	public TransferReason {
		if (value == null || value.strip().isEmpty()) {
			throw new TransferReasonRequiredException();
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("reason must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
