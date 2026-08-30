package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when a customer tax ID already belongs to another customer (R-C2, contract §8).
 * Maps to {@code 409 customer_tax_id_already_exists}.
 * Must not leak constraint names or values per §8.
 */
public class CustomerTaxIdAlreadyExistsException extends RuntimeException {

	public CustomerTaxIdAlreadyExistsException() {
		super("Customer tax ID already exists");
	}

	public CustomerTaxIdAlreadyExistsException(String message) {
		super(message);
	}
}
