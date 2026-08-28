package com.optiplant.inventory.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * List projection of a product (design §3.3, contract §6.2) — deliberately
 * <strong>without</strong> {@code units} and {@code description}, so a 100-row
 * page cannot trigger a per-row unit query.
 *
 * <p>It is a separate type rather than a {@link Product} with an empty
 * {@code units} list: an empty list would be ambiguous between "this product has
 * no units" and "we did not load them", and the first caller to iterate it would
 * silently get the wrong answer. Two types make the difference a compile-time
 * fact.
 */
public record ProductSummary(UUID externalId, Sku sku, String name, UnitCode baseUnit, boolean active,
		CategoryRef category, Instant createdAt, Instant updatedAt) {
}
