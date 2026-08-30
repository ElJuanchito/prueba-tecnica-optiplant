package com.optiplant.inventory.sales.domain.model;

/**
 * Monetary totals of a sale (design §4, §4.1, R-14).
 *
 * @param subtotal       the pre-discount sum of item list amounts
 * @param discountAmount the total discount applied across all items
 * @param taxAmount      the tax computed over the discounted subtotal
 * @param totalAmount    the final payable total: {@code subtotal - discountAmount + taxAmount}
 */
public record SaleTotals(Money subtotal, Money discountAmount, Money taxAmount, Money totalAmount) {

	public SaleTotals {
		if (subtotal == null) {
			throw new IllegalArgumentException("subtotal must not be null");
		}
		if (discountAmount == null) {
			throw new IllegalArgumentException("discountAmount must not be null");
		}
		if (taxAmount == null) {
			throw new IllegalArgumentException("taxAmount must not be null");
		}
		if (totalAmount == null) {
			throw new IllegalArgumentException("totalAmount must not be null");
		}
	}
}
