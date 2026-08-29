package com.optiplant.inventory.inventory.domain.model;

import java.util.List;

/** A page envelope over {@link KardexLine}, ordered by {@code created_at} ascending (R-16). */
public record KardexPage(List<KardexLine> content, long totalElements, int page, int size) {
}
