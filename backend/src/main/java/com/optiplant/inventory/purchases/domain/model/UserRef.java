package com.optiplant.inventory.purchases.domain.model;

import java.util.UUID;

/**
 * User reference descriptor for order details (contract §6): the {@code createdBy} actor.
 */
public record UserRef(UUID externalId, String username) {
}
