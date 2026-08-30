package com.optiplant.inventory.transfers.domain.model;

import java.util.regex.Pattern;

/**
 * The unique, human-legible transfer identifier, format {@code TRF-<yyyy>-<nnnn>} (design §3.1,
 * §6.2, D-3) — matching the seeded {@code TRF-2026-0001} and {@code transfer_number VARCHAR(50)
 * UNIQUE}. Allocation under a year-scoped advisory lock is a persistence concern (§6.2); this
 * type only enforces the shape.
 */
public record TransferNumber(String value) {

	private static final int MAX_LENGTH = 50;
	private static final Pattern SHAPE = Pattern.compile("TRF-\\d{4}-\\d{4,}");

	public TransferNumber {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("transfer number must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("transfer number must not exceed " + MAX_LENGTH + " characters");
		}
		if (!SHAPE.matcher(value).matches()) {
			throw new IllegalArgumentException("transfer number must match TRF-<yyyy>-<nnnn>, got " + value);
		}
	}
}
