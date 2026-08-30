package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Receive a dispatched transfer, invocable only from the <strong>destination</strong> (CU-TRA-04,
 * CU-TRA-05, design §5.1). One physical act resolving to {@code RECEIVED} or
 * {@code RECEIVED_WITH_DISCREPANCY} — contract §2.4.
 */
public interface ReceiveTransferUseCase {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     the transfer involves neither of the actor's branches
	 * @throws com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException
	 *     the actor's branch is not the destination (R-15)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException
	 *     the transfer is not {@code IN_TRANSIT} (R-01)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException
	 *     a received quantity is negative or above the dispatched one (R-19)
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException
	 *     a shortfall carries no reason (R-18)
	 */
	TransferDetail receive(AuthenticatedPrincipal actor, UUID transferExternalId, ReceiptCommand command);

	record ReceiptCommand(List<ReceiptLineCommand> items) {
	}

	record ReceiptLineCommand(UUID itemExternalId, BigDecimal receivedQuantity, String discrepancyReason) {
	}
}
