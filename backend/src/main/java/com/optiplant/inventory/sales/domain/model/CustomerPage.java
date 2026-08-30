package com.optiplant.inventory.sales.domain.model;

import java.util.List;

/**
 * Paginated customer listing result (contract §7, design §1).
 */
public record CustomerPage(
		List<Customer> content,
		long totalElements,
		int page,
		int size
) {
}
