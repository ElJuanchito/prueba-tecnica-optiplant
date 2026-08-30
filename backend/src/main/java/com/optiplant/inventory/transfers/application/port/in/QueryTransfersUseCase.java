package com.optiplant.inventory.transfers.application.port.in;

import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferDirection;
import com.optiplant.inventory.transfers.domain.model.TransferPage;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-side of {@code transfers} — listing and detail (contract §6, R-27, design §5.1). Own
 * branch on either side; {@code ADMIN} network-wide (RN-08).
 */
public interface QueryTransfersUseCase {

	TransferPage list(AuthenticatedPrincipal actor, TransferListQuery query);

	/**
	 * @throws com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException
	 *     unknown transfer, or one involving neither of the caller's branches
	 */
	TransferDetail detail(AuthenticatedPrincipal actor, UUID transferExternalId);

	record TransferListQuery(TransferStatus status, TransferDirection direction, Instant from, Instant to, int page,
			int size, String sort) {
	}
}
