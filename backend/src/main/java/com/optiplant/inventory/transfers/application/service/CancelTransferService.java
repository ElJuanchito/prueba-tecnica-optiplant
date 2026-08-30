package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.CancelTransferUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferReason;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy.Side;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates cancellation (CU-TRA-06), open to a manager of either side (R-21). Allowed only
 * from {@code REQUESTED} or {@code IN_PREPARATION} (R-22); no balance changes anywhere.
 *
 * <p>Audit {@code branchId} is the transfer's origin (T-03), regardless of which side cancelled.
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc.
 */
@Service
public class CancelTransferService implements CancelTransferUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public CancelTransferService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort,
			AuditWritePort auditWritePort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public TransferDetail cancel(AuthenticatedPrincipal actor, UUID transferExternalId, String reason) {
		Transfer transfer = transferRepository.lockForUpdate(transferExternalId)
				.orElseThrow(() -> new TransferNotFoundException(transferExternalId));
		TransferAccessPolicy.assertSide(actor, transfer, Side.EITHER);

		Transfer cancelled = transfer.cancel(new TransferReason(reason), Instant.now());
		Transfer saved = transferRepository.save(cancelled);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.originBranchExternalId(),
				"CANCEL_TRANSFER", "transfers", saved.externalId().toString(), null, null, null));

		return TransferDetailAssembler.toDetail(saved, referencePort);
	}
}
