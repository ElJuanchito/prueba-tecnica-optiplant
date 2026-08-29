package com.optiplant.inventory.inventory.domain.model;

import com.optiplant.inventory.inventory.domain.exception.AdjustmentReasonRequiredException;

/**
 * A mandatory, non-blank reason for a manual adjustment or write-off (RN-11, R-07). Trimmed and
 * bounded at {@code 500} characters — {@code kardex_movements.notes} is {@code TEXT}, but a
 * reason this long stops being an audit note.
 *
 * <p>A {@code null} or blank value throws {@link AdjustmentReasonRequiredException} so R-07 gets
 * its own error code rather than the generic {@code invalid_request}.
 */
public record MovementReason(String value) {

	private static final int MAX_LENGTH = 500;

	public MovementReason {
		if (value == null || value.strip().isEmpty()) {
			throw new AdjustmentReasonRequiredException();
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("reason must not exceed " + MAX_LENGTH + " characters");
		}
	}
}
