package com.optiplant.inventory.catalog.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of a catalog category (R-03). Immutable: a lifecycle
 * change is a {@code with*} copy, so no use case can hand a half-mutated
 * aggregate to the persistence adapter.
 *
 * <p>Carries {@code external_id} only, never the internal numeric {@code id}
 * (CLAUDE.md's anti-enumeration invariant). {@code updatedAt} is
 * application-maintained: the schema has no trigger, so {@link #withName} and
 * {@link #withActive} both advance it (R-03).
 *
 * <p>It deliberately does not carry {@code activeProductCount}: that number
 * belongs to {@code products}, not to the category, and embedding it would force
 * every mutation path to invent a value for it. {@link CategorySummary} pairs the
 * two only where the API needs them together.
 */
public record Category(UUID externalId, CategoryName name, String description, boolean active, Instant createdAt,
		Instant updatedAt) {

	/** Rename/re-describe; {@code updatedAt} advances to {@code now} (R-03). */
	public Category withName(CategoryName name, String description, Instant now) {
		return new Category(externalId, name, description, active, createdAt, now);
	}

	/** Flip the active flag; {@code updatedAt} advances to {@code now} (R-03). */
	public Category withActive(boolean active, Instant now) {
		return new Category(externalId, name, description, active, createdAt, now);
	}
}
