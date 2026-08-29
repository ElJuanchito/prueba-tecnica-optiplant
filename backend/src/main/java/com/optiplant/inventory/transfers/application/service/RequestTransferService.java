package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort.NewTransfer;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort.NewTransferItem;
import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.SameBranchTransferException;
import com.optiplant.inventory.transfers.domain.model.ProductReference;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a transfer request (CU-TRA-01): resolve the destination from the session
 * (R-05), validate origin and items (R-03), create with no balance effect (R-04), audit.
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — its out-ports have no adapter yet in S1;
 * registering it now would fail {@code ApplicationContextIT}'s full context boot, exactly as in
 * {@code add-inventory-module} S1.
 */
@Service
public class RequestTransferService implements RequestTransferUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public RequestTransferService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort,
			AuditWritePort auditWritePort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public TransferDetail request(AuthenticatedPrincipal actor, RequestTransferCommand command) {
		UUID destinationBranchExternalId = TransferAccessPolicy.resolveDestinationBranch(actor);
		UUID originBranchExternalId = command.originBranchExternalId();

		if (originBranchExternalId.equals(destinationBranchExternalId)) {
			throw new SameBranchTransferException();
		}
		referencePort.requireActiveBranch(originBranchExternalId);

		List<NewTransferItem> items = resolveItems(command.items());
		TransferNotes notes = TransferNotes.empty(command.priority());
		if (command.notes() != null && !command.notes().isBlank()) {
			notes = notes.withObservation(command.notes());
		}

		Transfer created = transferRepository.create(new NewTransfer(originBranchExternalId,
				destinationBranchExternalId, actor.userId(), notes, items));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), destinationBranchExternalId, "REQUEST_TRANSFER",
				"transfers", created.externalId().toString(), null, null, null));

		return TransferDetailAssembler.toDetail(created, referencePort);
	}

	private List<NewTransferItem> resolveItems(List<RequestedLine> lines) {
		Set<UUID> seenProducts = new HashSet<>();
		List<NewTransferItem> items = new ArrayList<>();
		for (RequestedLine line : lines) {
			if (!seenProducts.add(line.productExternalId())) {
				throw new DuplicateTransferItemException();
			}
			ProductReference product = referencePort.findProduct(line.productExternalId())
					.orElseThrow(() -> new ProductNotFoundException(line.productExternalId()));
			items.add(new NewTransferItem(product.externalId(), new TransferQuantity(line.requestedQuantity())));
		}
		return items;
	}
}
