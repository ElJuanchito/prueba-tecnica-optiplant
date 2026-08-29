package com.optiplant.inventory.logistics.domain.model;

import java.util.List;

/** A page envelope over {@link ActiveTransferView} (contract §6). */
public record ActiveTransferPage(List<ActiveTransferView> content, long totalElements, int page, int size) {
}
