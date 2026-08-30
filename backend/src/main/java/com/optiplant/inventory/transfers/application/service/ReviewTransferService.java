package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.ApprovedLine;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferReason;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy.Side;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates approval/adjustment and rejection (CU-TRA-02), invocable only from the origin
 * (R-06). Neither transition touches a balance (R-08); the lock is required by T-02 before any
 * transition.
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc.
 */
public class ReviewTransferService implements ReviewTransferUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public ReviewTransferService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort,
			AuditWritePort auditWritePort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public TransferDetail approve(AuthenticatedPrincipal actor, UUID transferExternalId, ApprovalCommand command) {
		Transfer transfer = lockAtOrigin(actor, transferExternalId);

		List<ApprovedLine> lines = command.items().stream()
				.map(line -> new ApprovedLine(line.itemExternalId(), line.approvedQuantity()))
				.toList();
		Transfer approved = transfer.approve(lines, Instant.now());
		Transfer saved = transferRepository.save(approved);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.originBranchExternalId(),
				"APPROVE_TRANSFER", "transfers", saved.externalId().toString(), null, null, null));

		return TransferDetailAssembler.toDetail(saved, referencePort);
	}

	@Override
	@Transactional
	public TransferDetail reject(AuthenticatedPrincipal actor, UUID transferExternalId, String reason) {
		Transfer transfer = lockAtOrigin(actor, transferExternalId);

		Transfer rejected = transfer.reject(new TransferReason(reason), Instant.now());
		Transfer saved = transferRepository.save(rejected);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.originBranchExternalId(), "REJECT_TRANSFER",
				"transfers", saved.externalId().toString(), null, null, null));

		return TransferDetailAssembler.toDetail(saved, referencePort);
	}

	private Transfer lockAtOrigin(AuthenticatedPrincipal actor, UUID transferExternalId) {
		Transfer transfer = transferRepository.lockForUpdate(transferExternalId)
				.orElseThrow(() -> new TransferNotFoundException(transferExternalId));
		TransferAccessPolicy.assertSide(actor, transfer, Side.ORIGIN);
		return transfer;
	}
}
