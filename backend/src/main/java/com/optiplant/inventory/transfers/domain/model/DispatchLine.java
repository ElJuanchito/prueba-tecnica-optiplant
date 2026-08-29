package com.optiplant.inventory.transfers.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One item's client-supplied dispatched quantity (CU-TRA-03, R-13). Validated and applied by
 * {@link com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy}, never raw.
 */
public record DispatchLine(UUID itemExternalId, BigDecimal dispatchedQuantity) {
}
