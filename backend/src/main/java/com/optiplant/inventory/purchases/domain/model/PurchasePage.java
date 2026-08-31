package com.optiplant.inventory.purchases.domain.model;

import java.util.List;

/**
 * A generic paginated result for every {@code purchases} listing (D-4): supplier listing,
 * order listing and cost history differ only in their element type. Envelope shape matches the
 * existing controllers — {@code { content, totalElements, page, size }} (contract §6).
 */
public record PurchasePage<T>(List<T> content, long totalElements, int page, int size) {

	public PurchasePage {
		content = content == null ? List.of() : List.copyOf(content);
	}
}
