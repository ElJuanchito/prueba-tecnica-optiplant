package com.optiplant.inventory.sales.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * An immutable line item in a sale (design §4).
 *
 * <p><strong>Compact constructor enforces {@code DT-05}'s mitigation (R-12):</strong>
 * <ul>
 *   <li>Rejects {@code unitPrice &gt; listUnitPrice} (mirroring {@code check_applied_price_not_above_list}).</li>
 *   <li>Rejects any {@code unitPrice} that is not {@code listUnitPrice * (1 - discountPercent/100)} at scale 4 {@code HALF_UP}.</li>
 *   <li>Rejects any {@code subtotal} that is not {@code quantity * unitPrice} at scale 4 {@code HALF_UP}.</li>
 * </ul>
 */
public record SaleItem(
		UUID externalId,
		UUID productExternalId,
		SaleQuantity quantity,
		Money listUnitPrice,
		Money unitPrice,
		DiscountPercent discountPercent,
		Money subtotal
) {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	public SaleItem {
		if (externalId == null) {
			throw new IllegalArgumentException("externalId must not be null");
		}
		if (productExternalId == null) {
			throw new IllegalArgumentException("productExternalId must not be null");
		}
		if (quantity == null) {
			throw new IllegalArgumentException("quantity must not be null");
		}
		if (listUnitPrice == null) {
			throw new IllegalArgumentException("listUnitPrice must not be null");
		}
		if (unitPrice == null) {
			throw new IllegalArgumentException("unitPrice must not be null");
		}
		if (discountPercent == null) {
			throw new IllegalArgumentException("discountPercent must not be null");
		}
		if (subtotal == null) {
			throw new IllegalArgumentException("subtotal must not be null");
		}

		if (unitPrice.value().compareTo(listUnitPrice.value()) > 0) {
			throw new IllegalArgumentException("unitPrice (" + unitPrice.value()
					+ ") must not exceed listUnitPrice (" + listUnitPrice.value() + ")");
		}

		BigDecimal factor = BigDecimal.ONE.subtract(discountPercent.value().divide(HUNDRED, 6, RoundingMode.HALF_UP));
		BigDecimal expectedUnitPrice = listUnitPrice.value().multiply(factor).setScale(4, RoundingMode.HALF_UP);
		if (unitPrice.value().compareTo(expectedUnitPrice) != 0) {
			throw new IllegalArgumentException("unitPrice (" + unitPrice.value()
					+ ") does not match listUnitPrice (" + listUnitPrice.value()
					+ ") after discount (" + discountPercent.value() + "%)");
		}

		BigDecimal expectedSubtotal = quantity.value().multiply(unitPrice.value()).setScale(4, RoundingMode.HALF_UP);
		if (subtotal.value().compareTo(expectedSubtotal) != 0) {
			throw new IllegalArgumentException("subtotal (" + subtotal.value()
					+ ") does not match quantity * unitPrice (" + expectedSubtotal + ")");
		}
	}
}
