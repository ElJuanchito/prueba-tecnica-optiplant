package com.optiplant.inventory.sales.domain.model;

import java.util.UUID;

/**
 * User reference descriptor for responsible seller in sales (contract §6).
 */
public record UserRef(UUID externalId, String username) {
}
