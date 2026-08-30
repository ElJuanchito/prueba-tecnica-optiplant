package com.optiplant.inventory.logistics.domain.model;

import java.util.List;

/** A page envelope over {@link RouteSummary} (contract §6). */
public record RoutePage(List<RouteSummary> content, long totalElements, int page, int size) {
}
