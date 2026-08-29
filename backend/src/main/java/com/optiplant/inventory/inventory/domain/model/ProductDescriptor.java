package com.optiplant.inventory.inventory.domain.model;

import java.util.UUID;

/**
 * The minimal product information {@code inventory} needs to enrich its own read models — sku
 * and display name, resolved from {@code catalog}'s {@code products} table through
 * {@code ProductLookupPort} without an {@code @Entity} spanning module boundaries (design §6.1).
 */
public record ProductDescriptor(UUID externalId, String sku, String name) {
}
