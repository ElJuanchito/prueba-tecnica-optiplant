package com.optiplant.inventory.sales.domain.model;

/**
 * Customer contact information (email, phone, address) (design §2).
 * Normalizes empty strings to null and validates maximum field lengths.
 */
public record CustomerContact(
		String email,
		String phone,
		String address
) {

	private static final int MAX_EMAIL = 100;
	private static final int MAX_PHONE = 50;
	private static final int MAX_ADDRESS = 255;

	public CustomerContact {
		email = normalize(email, MAX_EMAIL, "email");
		phone = normalize(phone, MAX_PHONE, "phone");
		address = normalize(address, MAX_ADDRESS, "address");
	}

	public static CustomerContact empty() {
		return new CustomerContact(null, null, null);
	}

	private static String normalize(String value, int maxLength, String fieldName) {
		if (value == null) {
			return null;
		}
		value = value.strip();
		if (value.isEmpty()) {
			return null;
		}
		if (value.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters");
		}
		return value;
	}
}
