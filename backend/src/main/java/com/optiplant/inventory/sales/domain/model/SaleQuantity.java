package com.optiplant.inventory.sales.domain.model;

import com.optiplant.inventory.sales.domain.exception.InvalidSaleQuantityException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A strictly positive quantity in the product's base unit (R-01, RN-13, design §4).
 * Normalized to scale 4 {@code HALF_UP}, matching {@code sale_items.quantity NUMERIC(14,4)}
 * and its {@code CHECK (quantity > 0)}.
 */
public record SaleQuantity(BigDecimal value) {

	private static final int SCALE = 4;

	public SaleQuantity {
		if (value == null) {
			throw new InvalidSaleQuantityException("Sale quantity must not be null");
		}
		value = value.setScale(SCALE, RoundingMode.HALF_UP);
		if (value.signum() <= 0) {
			throw new InvalidSaleQuantityException("Sale quantity must be strictly positive");
		}
	}

	public static SaleQuantity of(BigDecimal value) {
		return new SaleQuantity(value);
	}

	public static SaleQuantity of(String value) {
		return new SaleQuantity(new BigDecimal(value));
	}
}
