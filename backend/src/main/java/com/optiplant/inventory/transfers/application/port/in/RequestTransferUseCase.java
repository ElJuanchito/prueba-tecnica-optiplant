package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Request a transfer into the caller's own branch (CU-TRA-01, design §5.1). The session branch is
 * the <strong>destination</strong>; {@code originBranchExternalId} arrives as a body reference
 * (RN-14 binds only the acting branch to the session).
 */
public interface RequestTransferUseCase {

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.BranchContextRequiredException
	 *     {@code actor} is a corporate {@code ADMIN} (R-05)
	 * @throws com.optiplant.inventory.transfers.domain.exception.SameBranchTransferException
	 *     origin equals destination (R-03)
	 * @throws com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException
	 *     the same product appears twice (R-03)
	 * @throws com.optiplant.inventory.transfers.domain.exception.BranchNotFoundException
	 *     the origin branch is unknown or inactive (R-03)
	 * @throws com.optiplant.inventory.transfers.domain.exception.ProductNotFoundException
	 *     a product is unknown or disabled (R-03)
	 */
	TransferDetail request(AuthenticatedPrincipal actor, RequestTransferCommand command);

	record RequestTransferCommand(UUID originBranchExternalId, TransferPriority priority, String notes,
			List<RequestedLine> items) {
	}

	record RequestedLine(UUID productExternalId, BigDecimal requestedQuantity) {
	}
}
