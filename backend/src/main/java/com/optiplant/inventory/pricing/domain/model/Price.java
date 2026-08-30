package com.optiplant.inventory.pricing.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A price row in a price list for a product and optional branch scope (R-15, R-16, design §3).
 */
public record Price(
		UUID externalId,
		UUID priceListExternalId,
		UUID productExternalId,
		UUID branchExternalId,
		UnitPrice unitPrice,
		ValidityRange validity,
		Instant createdAt
) {

	public Price {
		if (externalId == null) {
			throw new IllegalArgumentException("externalId must not be null");
		}
		if (priceListExternalId == null) {
			throw new IllegalArgumentException("priceListExternalId must not be null");
		}
		if (productExternalId == null) {
			throw new IllegalArgumentException("productExternalId must not be null");
		}
		if (unitPrice == null) {
			throw new IllegalArgumentException("unitPrice must not be null");
		}
		if (validity == null) {
			throw new IllegalArgumentException("validity must not be null");
		}
	}

	public Price close(LocalDate validTo) {
		return new Price(
				this.externalId,
				this.priceListExternalId,
				this.productExternalId,
				this.branchExternalId,
				this.unitPrice,
				new ValidityRange(this.validity.from(), validTo),
				this.createdAt
		);
	}

	public PriceScope scope() {
		return branchExternalId == null ? PriceScope.CORPORATE : PriceScope.BRANCH;
	}
}
