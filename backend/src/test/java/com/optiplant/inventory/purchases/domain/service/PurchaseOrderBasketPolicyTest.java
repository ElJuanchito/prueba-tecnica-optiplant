package com.optiplant.inventory.purchases.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.DiscountOutOfRangeException;
import com.optiplant.inventory.purchases.domain.exception.DuplicateOrderItemException;
import com.optiplant.inventory.purchases.domain.exception.InvalidOrderQuantityException;
import com.optiplant.inventory.purchases.domain.exception.InvalidUnitCostException;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.PricedBasket;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.PricedLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.RawLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseOrderBasketPolicyTest {

	private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID P3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private static RawLine raw(UUID product, String qty, String unitCost, String discount) {
		return new RawLine(product, new BigDecimal(qty), null, new BigDecimal(unitCost),
				discount == null ? null : new BigDecimal(discount));
	}

	@Test
	@DisplayName("R-05: an empty basket is refused")
	void emptyBasketRefused() {
		assertThatThrownBy(() -> PurchaseOrderBasketPolicy.validateAndPrice(List.of(), Map.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-06: subtotals and total are computed from quantity, unit cost and discount")
	void totalsComputedServerSide() {
		PricedBasket basket = PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P1, "10", "5", "0"), raw(P2, "4", "10", "25")), Map.of());

		assertThat(basket.lines()).extracting(l -> l.subtotal().value())
				.containsExactly(new BigDecimal("50.0000"), new BigDecimal("30.0000"));
		assertThat(basket.totalAmount().value()).isEqualByComparingTo("80.0000");
	}

	@Test
	@DisplayName("R-08: the same product twice is refused")
	void duplicateProductRefused() {
		assertThatThrownBy(() -> PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P1, "1", "5", "0"), raw(P1, "2", "5", "0")), Map.of()))
				.isInstanceOf(DuplicateOrderItemException.class);
	}

	@Test
	@DisplayName("T-02: priced lines come back sorted ascending by product external_id")
	void linesSortedIntoLockOrder() {
		PricedBasket basket = PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P3, "1", "5", "0"), raw(P1, "1", "5", "0"), raw(P2, "1", "5", "0")), Map.of());

		assertThat(basket.lines()).extracting(PricedLine::productExternalId).containsExactly(P1, P2, P3);
	}

	@Test
	@DisplayName("R-05: a non-positive quantity, a negative cost and an out-of-range discount are refused")
	void lineInvariantsEnforced() {
		assertThatThrownBy(() -> PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P1, "0", "5", "0")), Map.of())).isInstanceOf(InvalidOrderQuantityException.class);

		assertThatThrownBy(() -> PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P1, "1", "-5", "0")), Map.of())).isInstanceOf(InvalidUnitCostException.class);

		assertThatThrownBy(() -> PurchaseOrderBasketPolicy.validateAndPrice(
				List.of(raw(P1, "1", "5", "150")), Map.of())).isInstanceOf(DiscountOutOfRangeException.class);
	}

	@Test
	@DisplayName("R-09: a line naming an alternative unit is converted to the base unit before pricing")
	void alternativeUnitConvertedBeforePricing() {
		UUID box = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
		RawLine line = new RawLine(P1, new BigDecimal("2"), box, new BigDecimal("10"), BigDecimal.ZERO);

		PricedBasket basket = PurchaseOrderBasketPolicy.validateAndPrice(List.of(line),
				Map.of(P1, new BigDecimal("12")));

		assertThat(basket.lines()).singleElement().satisfies(l -> {
			assertThat(l.orderedQuantity().value()).isEqualByComparingTo("24.0000");
			assertThat(l.subtotal().value()).isEqualByComparingTo("240.0000");
		});
	}
}
