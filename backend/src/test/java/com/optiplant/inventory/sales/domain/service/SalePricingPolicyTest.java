package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.sales.domain.model.TaxPercent;
import com.optiplant.inventory.sales.domain.service.SalePricingPolicy.PricedLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalePricingPolicyTest {

	@Test
	@DisplayName("R-12: Line pricing computes unitPrice and subtotal after discount at scale 4 HALF_UP")
	void priceLineCalculation() {
		UUID productId = UUID.randomUUID();
		SaleQuantity qty = SaleQuantity.of("3.0000");
		Money listPrice = Money.of("100.0000");
		DiscountPercent discount = DiscountPercent.of("10.00");

		PricedLine priced = SalePricingPolicy.priceLine(productId, qty, listPrice, discount);

		assertThat(priced.unitPrice().value()).isEqualByComparingTo("90.0000");
		assertThat(priced.subtotal().value()).isEqualByComparingTo("270.0000");
	}

	@Test
	@DisplayName("R-14: Totals calculation computes pre-discount subtotal, discount amount, and tax over discounted subtotal")
	void calculateTotalsWithTax() {
		// Item 1: 2 units @ $100 list price with 10% discount -> unitPrice = 90, subtotal = 180, listTotal = 200, discountTotal = 20
		PricedLine line1 = SalePricingPolicy.priceLine(
				UUID.randomUUID(),
				SaleQuantity.of("2.0000"),
				Money.of("100.0000"),
				DiscountPercent.of("10.00")
		);

		// Item 2: 1 unit @ $50 list price with 0% discount -> unitPrice = 50, subtotal = 50, listTotal = 50, discountTotal = 0
		PricedLine line2 = SalePricingPolicy.priceLine(
				UUID.randomUUID(),
				SaleQuantity.of("1.0000"),
				Money.of("50.0000"),
				DiscountPercent.ZERO
		);

		// Tax = 19% (VAT)
		TaxPercent tax = TaxPercent.of("19.00");

		SaleTotals totals = SalePricingPolicy.calculateTotals(List.of(line1, line2), tax);

		// Subtotal (pre-discount) = 200 + 50 = 250.0000
		assertThat(totals.subtotal().value()).isEqualByComparingTo("250.0000");

		// Discount Amount = 20 + 0 = 20.0000
		assertThat(totals.discountAmount().value()).isEqualByComparingTo("20.0000");

		// Discounted subtotal = 250 - 20 = 230.0000
		// Tax Amount = 230.0000 * 0.19 = 43.7000
		assertThat(totals.taxAmount().value()).isEqualByComparingTo("43.7000");

		// Total Amount = 230.0000 + 43.7000 = 273.7000
		assertThat(totals.totalAmount().value()).isEqualByComparingTo("273.7000");
	}

	@Test
	@DisplayName("R-14: Zero tax leaves totalAmount equal to discounted subtotal")
	void calculateTotalsZeroTax() {
		PricedLine line = SalePricingPolicy.priceLine(
				UUID.randomUUID(),
				SaleQuantity.of("5.0000"),
				Money.of("20.0000"),
				DiscountPercent.of("5.00")
		);

		SaleTotals totals = SalePricingPolicy.calculateTotals(List.of(line), TaxPercent.ZERO);

		// List subtotal: 5 * 20 = 100.0000
		assertThat(totals.subtotal().value()).isEqualByComparingTo("100.0000");
		// Discount: 5 * (20 - 19) = 5.0000
		assertThat(totals.discountAmount().value()).isEqualByComparingTo("5.0000");
		// Tax: 0
		assertThat(totals.taxAmount().value()).isEqualByComparingTo("0.0000");
		// Total: 95.0000
		assertThat(totals.totalAmount().value()).isEqualByComparingTo("95.0000");
	}
}
