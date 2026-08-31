package com.optiplant.inventory.purchases.domain.exception;

/**
 * A negative or absent unit cost (R-05, R-17, CU-COM-04 EX-02) — never defaulted to zero. Maps to
 * {@code 400 invalid_unit_cost}.
 */
public class InvalidUnitCostException extends RuntimeException {

	public InvalidUnitCostException(String message) {
		super(message);
	}
}
