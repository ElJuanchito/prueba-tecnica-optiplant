package com.optiplant.inventory.pricing.domain.service;

import com.optiplant.inventory.pricing.domain.exception.PricePeriodConflictException;
import com.optiplant.inventory.pricing.domain.model.Price;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Enforces price supersession invariants under R-16 and D-7 (design §3).
 *
 * <p>When setting a new price for (list, product, scope):
 * <ol>
 *   <li>If multiple open rows exist for the target scope, refuses with {@link PricePeriodConflictException}.</li>
 *   <li>If one open row exists, its {@code valid_from} must be strictly before {@code newValidFrom}; otherwise refuses with {@link PricePeriodConflictException}.</li>
 *   <li>Closes the open row with {@code valid_to = newValidFrom.minusDays(1)}.</li>
 * </ol>
 */
public final class PriceSupersessionPolicy {

	private PriceSupersessionPolicy() {
	}

	public record SupersessionPlan(Optional<Price> closedPrice, Price newPrice) {
	}

	/**
	 * Determines the supersession plan for a new price given currently open price rows.
	 *
	 * @param openPrices currently open price rows for the target (list, product, scope) tuple
	 * @param newPrice   the proposed new price row
	 * @return {@link SupersessionPlan} containing the closed price row (if any) and the new price row
	 * @throws PricePeriodConflictException if more than one open row exists, or if open row's validFrom &gt;= newValidFrom
	 */
	public static SupersessionPlan plan(List<Price> openPrices, Price newPrice) {
		if (openPrices == null || openPrices.isEmpty()) {
			return new SupersessionPlan(Optional.empty(), newPrice);
		}
		if (openPrices.size() > 1) {
			throw new PricePeriodConflictException(
					"Multiple active price rows already exist for list " + newPrice.priceListExternalId()
							+ ", product " + newPrice.productExternalId() + ", scope " + newPrice.scope());
		}
		Price currentOpen = openPrices.get(0);
		LocalDate newFrom = newPrice.validity().from();
		LocalDate currentFrom = currentOpen.validity().from();

		if (!currentFrom.isBefore(newFrom)) {
			throw new PricePeriodConflictException(
					"New price validFrom (" + newFrom + ") must be strictly after current price validFrom (" + currentFrom + ")");
		}

		Price closed = currentOpen.close(newFrom.minusDays(1));
		return new SupersessionPlan(Optional.of(closed), newPrice);
	}
}
