package com.optiplant.inventory.pricing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.pricing.domain.exception.PricePeriodConflictException;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import com.optiplant.inventory.pricing.domain.service.PriceSupersessionPolicy.SupersessionPlan;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceSupersessionPolicyTest {

	private static final UUID LIST_ID = UUID.randomUUID();
	private static final UUID PRODUCT_ID = UUID.randomUUID();
	private static final UUID BRANCH_ID = UUID.randomUUID();

	@Test
	@DisplayName("R-16: Normal supersession closes current open price at newValidFrom minus 1 day")
	void normalSupersessionClosesCurrentRow() {
		LocalDate currentFrom = LocalDate.of(2026, 1, 1);
		LocalDate newFrom = LocalDate.of(2026, 9, 1);

		Price openPrice = openPrice(currentFrom, "50.0000");
		Price newPrice = openPrice(newFrom, "60.0000");

		SupersessionPlan plan = PriceSupersessionPolicy.plan(List.of(openPrice), newPrice);

		assertThat(plan.closedPrice()).isPresent();
		Price closed = plan.closedPrice().get();
		assertThat(closed.validity().from()).isEqualTo(currentFrom);
		assertThat(closed.validity().to()).isEqualTo(LocalDate.of(2026, 8, 31));
		assertThat(plan.newPrice().validity().from()).isEqualTo(newFrom);
		assertThat(plan.newPrice().validity().to()).isNull();
	}

	@Test
	@DisplayName("R-16: When no open price exists, new price is accepted without closed price")
	void supersessionWithNoPriorOpenPrice() {
		LocalDate newFrom = LocalDate.of(2026, 9, 1);
		Price newPrice = openPrice(newFrom, "60.0000");

		SupersessionPlan plan = PriceSupersessionPolicy.plan(List.of(), newPrice);

		assertThat(plan.closedPrice()).isEmpty();
		assertThat(plan.newPrice()).isEqualTo(newPrice);
	}

	@Test
	@DisplayName("R-16 / T-07: Multiple open prices for the same scope are refused with PricePeriodConflictException")
	void multipleOpenPricesRefused() {
		Price open1 = openPrice(LocalDate.of(2026, 1, 1), "50.0000");
		Price open2 = openPrice(LocalDate.of(2026, 5, 1), "55.0000");
		Price newPrice = openPrice(LocalDate.of(2026, 9, 1), "60.0000");

		assertThatThrownBy(() -> PriceSupersessionPolicy.plan(List.of(open1, open2), newPrice))
				.isInstanceOf(PricePeriodConflictException.class)
				.hasMessageContaining("Multiple active price rows already exist");
	}

	@Test
	@DisplayName("R-16 / D-7: New validFrom equal to current validFrom is refused with PricePeriodConflictException")
	void newFromEqualToCurrentFromRefused() {
		LocalDate from = LocalDate.of(2026, 5, 1);
		Price open = openPrice(from, "50.0000");
		Price newPrice = openPrice(from, "60.0000");

		assertThatThrownBy(() -> PriceSupersessionPolicy.plan(List.of(open), newPrice))
				.isInstanceOf(PricePeriodConflictException.class)
				.hasMessageContaining("strictly after");
	}

	@Test
	@DisplayName("R-16 / D-7: New validFrom before current validFrom is refused with PricePeriodConflictException")
	void newFromBeforeCurrentFromRefused() {
		Price open = openPrice(LocalDate.of(2026, 5, 1), "50.0000");
		Price newPrice = openPrice(LocalDate.of(2026, 4, 1), "60.0000");

		assertThatThrownBy(() -> PriceSupersessionPolicy.plan(List.of(open), newPrice))
				.isInstanceOf(PricePeriodConflictException.class)
				.hasMessageContaining("strictly after");
	}

	private Price openPrice(LocalDate from, String amount) {
		return new Price(
				UUID.randomUUID(),
				LIST_ID,
				PRODUCT_ID,
				BRANCH_ID,
				UnitPrice.of(amount),
				ValidityRange.open(from),
				Instant.now()
		);
	}
}
