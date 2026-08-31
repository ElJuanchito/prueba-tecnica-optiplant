package com.optiplant.inventory.purchases.domain.model;

import java.util.regex.Pattern;

/**
 * A purchase order's internal number, format {@code OC-<yyyy>-<nnnn>} (F-9, PA-04). Non-blank,
 * at most 50 characters — enforces {@code purchase_orders.order_number VARCHAR(50) NOT NULL UNIQUE}.
 * Allocation of the sequence lives in the persistence adapter (S2, design §6.2); this type only
 * guards the shape.
 */
public record OrderNumber(String value) {

	private static final int MAX_LENGTH = 50;
	private static final Pattern FORMAT = Pattern.compile("^OC-\\d{4}-\\d+$");

	public OrderNumber {
		if (value == null) {
			throw new IllegalArgumentException("order number must not be null");
		}
		value = value.strip();
		if (value.length() > MAX_LENGTH) {
			throw new IllegalArgumentException("order number must not exceed " + MAX_LENGTH + " characters");
		}
		if (!FORMAT.matcher(value).matches()) {
			throw new IllegalArgumentException("order number must match OC-<yyyy>-<nnnn>, was: " + value);
		}
	}

	public static OrderNumber of(String value) {
		return new OrderNumber(value);
	}
}
