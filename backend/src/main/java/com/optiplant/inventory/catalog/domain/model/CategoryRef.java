package com.optiplant.inventory.catalog.domain.model;

import java.util.UUID;

/**
 * The cheap reference to a category that the product path embeds in its response
 * and carries on {@code Product} (design §3.3): just the {@code external_id}, the
 * display name and the active flag — never the internal numeric {@code id}, and
 * none of the audit/timestamp state a full {@code Category} holds.
 */
public record CategoryRef(UUID externalId, String name, boolean active) {
}
