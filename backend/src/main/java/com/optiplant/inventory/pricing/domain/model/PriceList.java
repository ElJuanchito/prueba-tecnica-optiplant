package com.optiplant.inventory.pricing.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A price list aggregate root (RF-VEN-03, design §3).
 *
 * <p>{@code isDefault} is immutable: seeded and guarded by {@code uq_price_lists_single_default}.
 * It has no mutator method.
 */
public record PriceList(
		UUID externalId,
		PriceListCode code,
		PriceListName name,
		String description,
		DiscountCap maxDiscountPercent,
		boolean isDefault,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {

	public PriceList {
		if (externalId == null) {
			throw new IllegalArgumentException("externalId must not be null");
		}
		if (code == null) {
			throw new IllegalArgumentException("code must not be null");
		}
		if (name == null) {
			throw new IllegalArgumentException("name must not be null");
		}
		if (maxDiscountPercent == null) {
			throw new IllegalArgumentException("maxDiscountPercent must not be null");
		}
	}

	public PriceList update(PriceListName newName, String newDescription, DiscountCap newCap) {
		return new PriceList(
				this.externalId,
				this.code,
				newName,
				newDescription,
				newCap,
				this.isDefault,
				this.active,
				this.createdAt,
				Instant.now()
		);
	}

	public PriceList deactivate() {
		return new PriceList(
				this.externalId,
				this.code,
				this.name,
				this.description,
				this.maxDiscountPercent,
				this.isDefault,
				false,
				this.createdAt,
				Instant.now()
		);
	}
}
