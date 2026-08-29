package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown by {@code Product}'s compact constructor when more than one inline unit
 * is marked as the default sale unit (R-14). Reachable through {@code POST
 * /products} with an inline unit list; the aggregate is rejected before any SQL
 * is issued, so the web layer maps it to {@code 400 invalid_request} — a
 * malformed client payload, never a {@code 500}.
 *
 * <p>A dedicated type rather than a bare {@code IllegalStateException} so the web
 * layer can dispatch on the class instead of matching the message text, mirroring
 * {@link DuplicateProductUnitException} for the other half of R-13/R-14. The
 * schema half of this invariant is the partial unique index
 * {@code uq_product_units_single_default} ({@code 01-init-schema.sql}).
 */
public class MultipleDefaultSaleUnitsException extends RuntimeException {

	public MultipleDefaultSaleUnitsException(String message) {
		super(message);
	}
}
