package com.optiplant.inventory.catalog.domain.exception;

import java.util.UUID;

/**
 * Thrown when a unit lookup names no unit of the product in scope — <strong>including
 * a unit that exists but hangs off another product</strong> (design §3.4, contract
 * §6.3). The web layer maps it to {@code 404 product_unit_not_found}: a unit of
 * another product must never surface as {@code 200}.
 */
public class ProductUnitNotFoundException extends RuntimeException {

	public ProductUnitNotFoundException(UUID unitExternalId) {
		super("No product unit found for external id " + unitExternalId);
	}
}
