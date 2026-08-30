package com.optiplant.inventory.purchases.domain.model;

import com.optiplant.inventory.purchases.domain.exception.InvalidUnitCostException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A non-negative monetary amount, scale 4 {@code HALF_UP} (D-5). One record for
 * {@code purchase_order_items.unit_cost}, {@code purchase_order_items.subtotal} and
 * {@code purchase_orders.total_amount} — all {@code NUMERIC(14,4) CHECK (>= 0)}.
 *
 * <p>A {@code null} or negative value throws {@link InvalidUnitCostException} so a bad line cost
 * surfaces as {@code invalid_unit_cost} (R-05, R-17, CU-COM-04 EX-02), never defaulted to zero.
 */
public record Money(BigDecimal value) {

	public static final Money ZERO = new Money(BigDecimal.ZERO);

	public Money {
		if (value == null) {
			throw new InvalidUnitCostException("monetary amount must not be null");
		}
		value = value.setScale(4, RoundingMode.HALF_UP);
		if (value.signum() < 0) {
			throw new InvalidUnitCostException("monetary amount must not be negative");
		}
	}

	public static Money of(BigDecimal value) {
		return new Money(value);
	}

	public static Money of(String value) {
		return new Money(new BigDecimal(value));
	}

	public Money add(Money other) {
		return new Money(this.value.add(other.value));
	}

	public Money multiply(BigDecimal factor) {
		return new Money(this.value.multiply(factor));
	}
}
