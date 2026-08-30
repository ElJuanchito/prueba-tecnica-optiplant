package com.optiplant.inventory.shared.price;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An applied price list descriptor with its discount cap (design §2, P-05).
 * The cap travels with the list so that callers cannot apply a discount they cannot validate (RN-17).
 *
 * @param externalId         the price list external ID
 * @param code               the price list code
 * @param maxDiscountPercent the maximum discount percentage allowed under this list (0..100)
 */
public record AppliedPriceList(UUID externalId, String code, BigDecimal maxDiscountPercent) {
}
