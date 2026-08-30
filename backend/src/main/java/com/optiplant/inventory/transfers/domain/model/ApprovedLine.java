package com.optiplant.inventory.transfers.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item's client-supplied approved quantity (CU-TRA-02, R-07). Validated and applied by
 * {@link com.optiplant.inventory.transfers.domain.service.TransferApprovalPolicy}, never raw.
 */
public record ApprovedLine(UUID itemExternalId, BigDecimal approvedQuantity) {
}
