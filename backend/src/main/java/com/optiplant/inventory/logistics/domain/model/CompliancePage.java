package com.optiplant.inventory.logistics.domain.model;

import java.util.List;

/** A page envelope over {@link ComplianceRow} (contract §6). */
public record CompliancePage(List<ComplianceRow> content, long totalElements, int page, int size) {
}
