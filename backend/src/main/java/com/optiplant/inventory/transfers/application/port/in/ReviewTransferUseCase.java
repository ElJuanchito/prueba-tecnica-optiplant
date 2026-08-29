package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Approve, adjust or reject a requested transfer, invocable only from the <strong>origin</strong>
 * (CU-TRA-02, design §5.1).
 */
public interface ReviewTransferUseCase {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     the transfer involves neither of the actor's branches
	 * @throws com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException
	 *     the actor's branch is not the origin (R-06)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException
	 *     the transfer is not {@code REQUESTED} (R-01)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException
	 *     an approved quantity is not {@code > 0} and {@code <=} requested (R-07)
	 */
	TransferDetail approve(AuthenticatedPrincipal actor, UUID transferExternalId, ApprovalCommand command);

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     the transfer involves neither of the actor's branches
	 * @throws com.optiplant.inventory.transfers.domain.exception.CrossBranchAccessDeniedException
	 *     the actor's branch is not the origin (R-06)
	 * @throws com.optiplant.inventory.transfers.domain.exception.InvalidTransferStateException
	 *     the transfer is not {@code REQUESTED} (R-01)
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferReasonRequiredException
	 *     a blank or absent reason (R-09)
	 */
	TransferDetail reject(AuthenticatedPrincipal actor, UUID transferExternalId, String reason);

	record ApprovalCommand(List<ApprovedLineCommand> items, String notes) {
	}

	record ApprovedLineCommand(UUID itemExternalId, BigDecimal approvedQuantity) {
	}
}
