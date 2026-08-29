package com.optiplant.inventory.inventory.domain.model;

import java.util.List;

/** A page envelope over {@link StockLine} (contract §6, matches the existing {@code catalog} shape). */
public record StockPage(List<StockLine> content, long totalElements, int page, int size) {
}
