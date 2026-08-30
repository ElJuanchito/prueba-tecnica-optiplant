package com.optiplant.inventory.transfers.domain.model;

import java.util.UUID;

/** A product as resolved by {@code TransferReferencePort}, enriching a transfer item view. */
public record ProductReference(UUID externalId, String sku, String name) {
}
