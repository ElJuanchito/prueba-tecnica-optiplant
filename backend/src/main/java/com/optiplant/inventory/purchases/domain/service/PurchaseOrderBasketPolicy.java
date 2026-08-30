package com.optiplant.inventory.purchases.domain.service;

import com.optiplant.inventory.purchases.domain.exception.DuplicateOrderItemException;
import com.optiplant.inventory.purchases.domain.model.DiscountPercent;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.PurchaseQuantity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates the requested line set and prices it (R-05, R-06, R-08, R-09, design §3.4): non-empty,
 * no duplicated product, {@code orderedQuantity > 0}, {@code unitCost >= 0}, discount in range;
 * converts every quantity to the base unit; computes each {@code subtotal = quantity × unitCost ×
 * (1 − discount/100)} and {@code totalAmount} as their sum (R-06 — client totals never reach it).
 *
 * <p>Returns the priced lines <strong>already sorted ascending by product {@code external_id}</strong>,
 * so the service cannot get the T-02 lock order wrong. Serves creation and the R-10 edit alike.
 */
public final class PurchaseOrderBasketPolicy {

	private PurchaseOrderBasketPolicy() {
	}

	/** A client-supplied order line before validation and pricing. */
	public record RawLine(UUID productExternalId, BigDecimal orderedQuantity, UUID unitOfMeasureExternalId,
			BigDecimal unitCost, BigDecimal discountPercent) {
	}

	/** A validated, base-unit, priced line. */
	public record PricedLine(UUID productExternalId, PurchaseQuantity orderedQuantity, Money unitCost,
			DiscountPercent discountPercent, Money subtotal) {
	}

	/** The priced basket: lines in T-02 lock order and the server-computed total (R-06). */
	public record PricedBasket(List<PricedLine> lines, Money totalAmount) {
	}

	/**
	 * @param lines                    the requested lines
	 * @param conversionFactorByProduct product {@code external_id} to its {@code conversion_factor},
	 *                                  only needed for lines naming a non-base unit (R-09)
	 */
	public static PricedBasket validateAndPrice(Collection<RawLine> lines,
			Map<UUID, BigDecimal> conversionFactorByProduct) {
		if (lines == null || lines.isEmpty()) {
			throw new IllegalArgumentException("a purchase order must contain at least one item");
		}
		Map<UUID, BigDecimal> factors = conversionFactorByProduct == null ? Map.of() : conversionFactorByProduct;

		Set<UUID> seen = new HashSet<>();
		List<PricedLine> priced = new ArrayList<>();
		Money total = Money.ZERO;

		for (RawLine line : lines) {
			if (line == null || line.productExternalId() == null) {
				throw new IllegalArgumentException("an order line must name a product");
			}
			if (!seen.add(line.productExternalId())) {
				throw new DuplicateOrderItemException(line.productExternalId());
			}

			PurchaseQuantity baseQuantity = UnitConversionPolicy.toBaseUnit(line.productExternalId(),
					line.unitOfMeasureExternalId(), line.orderedQuantity(),
					factors.get(line.productExternalId()));
			Money unitCost = Money.of(line.unitCost());
			DiscountPercent discount = DiscountPercent.of(line.discountPercent());

			Money effectiveUnitCost = unitCost.multiply(discount.complementFactor());
			Money subtotal = new Money(baseQuantity.value().multiply(effectiveUnitCost.value()));

			priced.add(new PricedLine(line.productExternalId(), baseQuantity, unitCost, discount, subtotal));
			total = total.add(subtotal);
		}

		priced.sort(Comparator.comparing(PricedLine::productExternalId));
		return new PricedBasket(List.copyOf(priced), total);
	}
}
