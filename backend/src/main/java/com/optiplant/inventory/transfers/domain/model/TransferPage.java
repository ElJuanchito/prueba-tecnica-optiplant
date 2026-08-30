package com.optiplant.inventory.transfers.domain.model;

import java.util.List;

/** A page envelope over {@link TransferSummary} (contract §6, matches the existing controllers' shape). */
public record TransferPage(List<TransferSummary> content, long totalElements, int page, int size) {
}
