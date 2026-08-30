package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Cancel a transfer before dispatch, open to a manager of <strong>either</strong> side (CU-TRA-06,
 * design §5.1, R-21).
 */
public interface CancelTransferUseCase {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     the transfer involves neither of the actor's branches
	 * @throws com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException
	 *     the actor's branch is neither the origin nor the destination
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException
	 *     the transfer is not {@code REQUESTED} or {@code IN_PREPARATION} (R-01, R-22)
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException
	 *     a blank or absent reason (R-21)
	 */
	TransferDetail cancel(AuthenticatedPrincipal actor, UUID transferExternalId, String reason);
}
