package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dispatch a prepared transfer, invocable only from the <strong>origin</strong> (CU-TRA-03, design §5.1). */
public interface DispatchTransferUseCase {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     the transfer involves neither of the actor's branches
	 * @throws com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException
	 *     the actor's branch is not the origin (R-10)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException
	 *     the transfer is not {@code IN_PREPARATION} (R-01, R-14)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException
	 *     a dispatched quantity is not {@code > 0} and {@code <=} the agreed quantity (R-13)
	 * @throws com.optiplant.inventory.shared.stock.StockMutationRejectedException
	 *     origin stock is insufficient (R-12) — mapped to {@code insufficient_stock}
	 */
	TransferDetail dispatch(AuthenticatedPrincipal actor, UUID transferExternalId, DispatchCommand command);

	record DispatchCommand(String carrierName, String trackingNumber, Instant estimatedArrivalAt,
			List<DispatchLineCommand> items) {
	}

	record DispatchLineCommand(UUID itemExternalId, BigDecimal dispatchedQuantity) {
	}
}
