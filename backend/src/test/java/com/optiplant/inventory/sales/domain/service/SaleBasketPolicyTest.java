package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.DuplicateSaleItemException;
import com.optiplant.inventory.sales.domain.exception.InvalidSaleQuantityException;
import com.optiplant.inventory.sales.domain.service.SaleBasketPolicy.RawBasketItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleBasketPolicyTest {

	@Test
	@DisplayName("R-01: Empty basket throws IllegalArgumentException")
	void emptyBasketThrows() {
		assertThatThrownBy(() -> SaleBasketPolicy.validateAndSort(List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least one item");
	}

	@Test
	@DisplayName("R-01: Quantity <= 0 throws InvalidSaleQuantityException")
	void nonPositiveQuantityThrows() {
		UUID product = UUID.randomUUID();
		assertThatThrownBy(() -> new RawBasketItem(product, BigDecimal.ZERO, null, null))
				.isInstanceOf(InvalidSaleQuantityException.class);

		assertThatThrownBy(() -> new RawBasketItem(product, new BigDecimal("-1.0000"), null, null))
				.isInstanceOf(InvalidSaleQuantityException.class);
	}

	@Test
	@DisplayName("R-06: Duplicate product in basket throws DuplicateSaleItemException")
	void duplicateProductThrows() {
		UUID product = UUID.randomUUID();
		RawBasketItem item1 = new RawBasketItem(product, new BigDecimal("1.0000"), null, null);
		RawBasketItem item2 = new RawBasketItem(product, new BigDecimal("2.0000"), null, null);

		assertThatThrownBy(() -> SaleBasketPolicy.validateAndSort(List.of(item1, item2)))
				.isInstanceOf(DuplicateSaleItemException.class);
	}

	@Test
	@DisplayName("T-02: Valid basket returns lines sorted ascending by product external_id (lock order)")
	void validBasketSortedByProductId() {
		UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
		UUID p3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

		RawBasketItem item3 = new RawBasketItem(p3, new BigDecimal("1.0000"), null, null);
		RawBasketItem item1 = new RawBasketItem(p1, new BigDecimal("2.0000"), null, null);
		RawBasketItem item2 = new RawBasketItem(p2, new BigDecimal("3.0000"), null, null);

		List<RawBasketItem> sorted = SaleBasketPolicy.validateAndSort(List.of(item3, item1, item2));

		assertThat(sorted).extracting(RawBasketItem::productExternalId)
				.containsExactly(p1, p2, p3);
	}
}
