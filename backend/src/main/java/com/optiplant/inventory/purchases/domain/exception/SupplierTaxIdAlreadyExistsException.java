package com.optiplant.inventory.purchases.domain.exception;

/**
 * {@code suppliers.tax_id} is {@code UNIQUE} and the value is already stored (R-01). Maps to
 * {@code 409 supplier_tax_id_already_exists}. The message names neither the constraint nor the value.
 */
public class SupplierTaxIdAlreadyExistsException extends RuntimeException {

	public SupplierTaxIdAlreadyExistsException() {
		super("A supplier with this tax id already exists");
	}

	public SupplierTaxIdAlreadyExistsException(String message) {
		super(message);
	}
}
