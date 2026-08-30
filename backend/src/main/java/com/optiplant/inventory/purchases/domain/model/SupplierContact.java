package com.optiplant.inventory.purchases.domain.model;

/**
 * A supplier's optional contact data (design §3.1). Blank fields normalize to {@code null};
 * lengths mirror {@code suppliers}: {@code contact_name}/{@code email} at most 100,
 * {@code phone} at most 50, {@code address} at most 255.
 */
public record SupplierContact(String contactName, String email, String phone, String address) {

	private static final int MAX_CONTACT_NAME = 100;
	private static final int MAX_EMAIL = 100;
	private static final int MAX_PHONE = 50;
	private static final int MAX_ADDRESS = 255;

	public SupplierContact {
		contactName = normalize(contactName, MAX_CONTACT_NAME, "contactName");
		email = normalize(email, MAX_EMAIL, "email");
		phone = normalize(phone, MAX_PHONE, "phone");
		address = normalize(address, MAX_ADDRESS, "address");
	}

	public static SupplierContact empty() {
		return new SupplierContact(null, null, null, null);
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
