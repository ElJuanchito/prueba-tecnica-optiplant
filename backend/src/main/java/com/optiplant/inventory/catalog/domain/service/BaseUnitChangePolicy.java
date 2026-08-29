package com.optiplant.inventory.catalog.domain.service;

import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException;
import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException.Reason;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.StockPresence;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import java.time.Instant;

/**
 * The R-08 rule: a product's base unit may change only when the product has
 * neither balances nor movement history (design §4.2). Pure domain — no
 * {@code org.springframework..}, no {@code jakarta.persistence..} — and it takes a
 * {@link StockPresence} enum rather than the {@code shared/stock} port or an
 * {@code Optional}: with three explicit values the refusal is a case in a switch,
 * every R-08 scenario is unit-testable with no stub, and there is no
 * {@code orElse(...)} whose default could silently let the change through (D-3,
 * contract §2.2 "MUST NOT fail open").
 *
 * <p>The mapping from the {@code Optional<ProductStockPresencePort>} bean to one
 * of the three values lives in {@code ProductAdminService} (design §5.2), covered
 * by its own unit test with a stubbed port.
 */
public final class BaseUnitChangePolicy {

	private BaseUnitChangePolicy() {
	}

	/**
	 * Applies {@code newBaseUnit} to {@code product} when {@code presence} is
	 * {@link StockPresence#UNTOUCHED}; otherwise refuses.
	 *
	 * @return {@code product.withBaseUnit(newBaseUnit, now)} — a copy whose
	 *     {@code updatedAt} is advanced to {@code now}
	 * @throws BaseUnitChangeRejectedException with {@link Reason#HAS_HISTORY} when
	 *     {@code presence} is {@link StockPresence#HAS_HISTORY}, or with
	 *     {@link Reason#PRECONDITION_UNVERIFIABLE} when {@code presence} is
	 *     {@link StockPresence#UNKNOWN}. On refusal no field of {@code product} is
	 *     touched — the caller's aggregate is unchanged (R-08).
	 */
	public static Product apply(Product product, UnitCode newBaseUnit, StockPresence presence, Instant now) {
		return switch (presence) {
			case UNTOUCHED -> product.withBaseUnit(newBaseUnit, now);
			case HAS_HISTORY -> throw new BaseUnitChangeRejectedException(Reason.HAS_HISTORY);
			case UNKNOWN -> throw new BaseUnitChangeRejectedException(Reason.PRECONDITION_UNVERIFIABLE);
		};
	}
}
