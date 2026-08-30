package com.optiplant.inventory.sales.domain.model;

import java.util.regex.Pattern;

/**
 * A sale's unique commercial invoice number (R-01, D-5, design §4, §6.3).
 *
 * <p>Enforces {@code sales.invoice_number VARCHAR(50) NOT NULL UNIQUE}.
 * {@link #isReservedInternal()} checks whether the string matches the internal format
 * {@code VEN-\d{4}-\d+}, which the external POS intake must not use (D-5).
 */
public record InvoiceNumber(String value) {

	private static final int MAX_LENGTH = 50;
	private static final Pattern RESERVED_PATTERN = Pattern.compile("^VEN-\\d{4}-\\d+$");

	public InvoiceNumber {
		if (value == null) {
			throw new IllegalArgumentException("invoiceNumber must not be null");
		}
		value = value.strip();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("invoiceNumber must not be blank");
		}
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("invoiceNumber must not exceed " + MAX_LENGTH + " characters");
		}
	}

	public static InvoiceNumber of(String value) {
		return new InvoiceNumber(value);
	}

	/**
	 * Returns {@code true} if this invoice number matches the internal reserved format {@code VEN-<yyyy>-<nnnn>}.
	 */
	public boolean isReservedInternal() {
		return RESERVED_PATTERN.matcher(value).matches();
	}
}
