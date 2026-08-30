package com.optiplant.inventory.sales.domain.exception;

/**
 * Thrown when a supplied external POS invoice number already exists (R-29).
 * Maps to {@code 409 duplicate_invoice_number}.
 */
public class DuplicateInvoiceNumberException extends RuntimeException {

	public DuplicateInvoiceNumberException(String invoiceNumber) {
		super("Duplicate invoice number: " + invoiceNumber);
	}
}
