package com.optiplant.inventory.catalog.domain.model;

/**
 * Read projection pairing a {@link Category} with its {@code activeProductCount}
 * — derived data the category does not own (design §3.3, §6.1). The count is of
 * <strong>active</strong> products specifically, because only those block R-04's
 * category disable, so a client can anticipate the {@code 409} before issuing it.
 */
public record CategorySummary(Category category, long activeProductCount) {
}
