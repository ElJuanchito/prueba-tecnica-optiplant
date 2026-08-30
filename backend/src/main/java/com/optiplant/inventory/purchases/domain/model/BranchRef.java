package com.optiplant.inventory.purchases.domain.model;

import java.util.UUID;

/**
 * Branch reference descriptor for order details and listings (contract §6). Declared locally —
 * importing {@code sales}' would violate boundary rule 3 (design §3.2).
 */
public record BranchRef(UUID externalId, String name) {
}
