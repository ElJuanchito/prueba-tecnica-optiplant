package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.sales.domain.model.TaxPercent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Pure calculation of line amounts and sale totals (R-12, R-14, design §4.1).
 *
 * <p>Per line:
 * <ul>
 *   <li>{@code unitPrice = listUnitPrice * (1 - discountPercent / 100)}, scale 4 HALF_UP</li>
 *   <li>{@code subtotal = quantity * unitPrice}, scale 4 HALF_UP</li>
 * </ul>
 *
 * <p>Per sale:
 * <ul>
 *   <li>{@code subtotal = sum(quantity * listUnitPrice)} (pre-discount amount)</li>
 *   <li>{@code discountAmount = sum(quantity * (listUnitPrice - unitPrice))}</li>
 *   <li>{@code taxAmount = (subtotal - discountAmount) * taxPercent / 100}</li>
 *   <li>{@code totalAmount = subtotal - discountAmount + taxAmount}</li>
 * </ul>
 */
public final class SalePricingPolicy {

	private static final BigDecimal HUNDRED = new BigDecimal("100");

	private SalePricingPolicy() {
	}

	public record PricedLine(
			UUID productExternalId,
			SaleQuantity quantity,
			Money listUnitPrice,
			Money unitPrice,
			DiscountPercent discountPercent,
			Money subtotal
	) {
	}

	public static PricedLine priceLine(
			UUID productExternalId,
			SaleQuantity quantity,
			Money listUnitPrice,
			DiscountPercent discountPercent
	) {
		if (productExternalId == null) {
			throw new IllegalArgumentException("productExternalId must not be null");
		}
		if (quantity == null) {
			throw new IllegalArgumentException("quantity must not be null");
		}
		if (listUnitPrice == null) {
			throw new IllegalArgumentException("listUnitPrice must not be null");
		}
		DiscountPercent discount = discountPercent == null ? DiscountPercent.ZERO : discountPercent;

		BigDecimal factor = BigDecimal.ONE.subtract(discount.value().divide(HUNDRED, 6, RoundingMode.HALF_UP));
		Money unitPrice = new Money(listUnitPrice.value().multiply(factor).setScale(4, RoundingMode.HALF_UP));
		Money subtotal = new Money(quantity.value().multiply(unitPrice.value()).setScale(4, RoundingMode.HALF_UP));

		return new PricedLine(productExternalId, quantity, listUnitPrice, unitPrice, discount, subtotal);
	}

	public static SaleTotals calculateTotals(List<PricedLine> lines, TaxPercent taxPercent) {
		if (lines == null || lines.isEmpty()) {
			throw new IllegalArgumentException("lines must not be empty");
		}
		TaxPercent tax = taxPercent == null ? TaxPercent.ZERO : taxPercent;

		BigDecimal subtotalSum = BigDecimal.ZERO;
		BigDecimal discountAmountSum = BigDecimal.ZERO;

		for (PricedLine line : lines) {
			BigDecimal lineListTotal = line.quantity().value().multiply(line.listUnitPrice().value());
			BigDecimal lineDiscountTotal = line.quantity().value().multiply(
					line.listUnitPrice().value().subtract(line.unitPrice().value())
			);
			subtotalSum = subtotalSum.add(lineListTotal);
			discountAmountSum = discountAmountSum.add(lineDiscountTotal);
		}

		subtotalSum = subtotalSum.setScale(4, RoundingMode.HALF_UP);
		discountAmountSum = discountAmountSum.setScale(4, RoundingMode.HALF_UP);

		BigDecimal discountedSubtotal = subtotalSum.subtract(discountAmountSum);
		BigDecimal taxAmount = discountedSubtotal.multiply(tax.value().divide(HUNDRED, 6, RoundingMode.HALF_UP))
				.setScale(4, RoundingMode.HALF_UP);
		BigDecimal totalAmount = discountedSubtotal.add(taxAmount).setScale(4, RoundingMode.HALF_UP);

		return new SaleTotals(
				new Money(subtotalSum),
				new Money(discountAmountSum),
				new Money(taxAmount),
				new Money(totalAmount)
		);
	}
}
