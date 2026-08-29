package com.optiplant.inventory.transfers.application.port.out;

import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDirection;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferPage;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for {@code transfers} / {@code transfer_items} persistence (design §5.2).
 * Named for the need, not the technology: only {@code external_id}-shaped {@link UUID}s and
 * domain types cross it.
 */
public interface TransferRepositoryPort {

	/** Allocates the {@code TRF-<yyyy>-<nnnn>} number under a year-scoped advisory lock (§6.2, D-3) and inserts. */
	Transfer create(NewTransfer newTransfer);

	/** {@code SELECT ... FOR UPDATE} (T-02, F-5) — required before any transition. */
	Optional<Transfer> lockForUpdate(UUID externalId);

	/** No lock (T-05, RN-09). */
	Optional<Transfer> findByExternalId(UUID externalId);

	Transfer save(Transfer transfer);

	/** No lock (T-05, RN-09). */
	TransferPage list(TransferFilter filter);

	record NewTransfer(UUID originBranchExternalId, UUID destinationBranchExternalId,
			UUID requestedByUserExternalId, TransferNotes notes, List<NewTransferItem> items) {
	}

	record NewTransferItem(UUID productExternalId, TransferQuantity requestedQuantity) {
	}

	/**
	 * @param callerBranchExternalId the caller's own branch, {@code null} for a network-wide {@code ADMIN} (RN-08)
	 * @param direction               {@code null} for either side
	 */
	record TransferFilter(UUID callerBranchExternalId, TransferStatus status, TransferDirection direction,
			Instant from, Instant to, String sort, int page, int size) {
	}
}
