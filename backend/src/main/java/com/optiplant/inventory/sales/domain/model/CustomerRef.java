package com.optiplant.inventory.sales.domain.model;

import java.util.UUID;

/**
 * View reference descriptor for customer in sale responses (contract §7, design §1).
 */
public record CustomerRef(
		UUID externalId,
		String name,
		String taxId
) {
}
