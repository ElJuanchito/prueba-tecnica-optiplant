package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.exception.DuplicateSaleItemException;
import com.optiplant.inventory.sales.domain.exception.InvalidSaleQuantityException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Validates sale basket items and returns them in deterministic lock order (R-01, R-06, T-02, design §4.1).
 *
 * <p>Enforces:
 * <ul>
 *   <li>At least one item in the basket.</li>
 *   <li>No duplicate product {@code external_id}.</li>
 *   <li>Quantity strictly positive.</li>
 *   <li>Lines returned sorted ascending by product {@code external_id} (T-02 lock order).</li>
 * </ul>
 */
public final class SaleBasketPolicy {

	private SaleBasketPolicy() {
	}

	public record RawBasketItem(
			UUID productExternalId,
			BigDecimal quantity,
			UUID unitOfMeasureExternalId,
			BigDecimal discountPercent
	) {
		public RawBasketItem {
			if (productExternalId == null) {
				throw new IllegalArgumentException("productExternalId must not be null");
			}
			if (quantity == null) {
				throw new InvalidSaleQuantityException("quantity must not be null");
			}
			if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
				throw new InvalidSaleQuantityException("quantity must be strictly positive");
			}
		}
	}

	public static List<RawBasketItem> validateAndSort(Collection<RawBasketItem> items) {
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("Sale basket must contain at least one item");
		}
		Set<UUID> seenProducts = new HashSet<>();
		for (RawBasketItem item : items) {
			if (item == null) {
				throw new IllegalArgumentException("Basket item must not be null");
			}
			if (!seenProducts.add(item.productExternalId())) {
				throw new DuplicateSaleItemException(item.productExternalId());
			}
		}
		return items.stream()
				.sorted(Comparator.comparing(RawBasketItem::productExternalId))
				.toList();
	}
}
