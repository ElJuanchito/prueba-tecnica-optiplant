package com.optiplant.inventory.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleItemTest {

	private static final UUID ITEM_ID = UUID.randomUUID();
	private static final UUID PRODUCT_ID = UUID.randomUUID();

	@Test
	@DisplayName("R-12 / DT-05: Consistent SaleItem constructs successfully")
	void validSaleItemConstructs() {
		assertThatCode(() -> new SaleItem(
				ITEM_ID,
				PRODUCT_ID,
				SaleQuantity.of("2.0000"),
				Money.of("100.0000"),
				Money.of("80.0000"),
				DiscountPercent.of("20.00"),
				Money.of("160.0000")
		)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-12 / DT-05: unitPrice greater than listUnitPrice is rejected")
	void unitPriceAboveListPriceRejected() {
		assertThatThrownBy(() -> new SaleItem(
				ITEM_ID,
				PRODUCT_ID,
				SaleQuantity.of("1.0000"),
				Money.of("100.0000"),
				Money.of("110.0000"),
				DiscountPercent.ZERO,
				Money.of("110.0000")
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not exceed listUnitPrice");
	}

	@Test
	@DisplayName("R-12 / DT-05: unitPrice inconsistent with discount is rejected")
	void unitPriceInconsistentWithDiscountRejected() {
		// List price $100 with 20% discount should have unitPrice = $80, but $85 is passed
		assertThatThrownBy(() -> new SaleItem(
				ITEM_ID,
				PRODUCT_ID,
				SaleQuantity.of("1.0000"),
				Money.of("100.0000"),
				Money.of("85.0000"),
				DiscountPercent.of("20.00"),
				Money.of("85.0000")
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match listUnitPrice");
	}

	@Test
	@DisplayName("R-12: subtotal inconsistent with quantity * unitPrice is rejected")
	void subtotalInconsistentRejected() {
		assertThatThrownBy(() -> new SaleItem(
				ITEM_ID,
				PRODUCT_ID,
				SaleQuantity.of("2.0000"),
				Money.of("100.0000"),
				Money.of("100.0000"),
				DiscountPercent.ZERO,
				Money.of("150.0000") // should be 200.0000
		)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match quantity * unitPrice");
	}
}
