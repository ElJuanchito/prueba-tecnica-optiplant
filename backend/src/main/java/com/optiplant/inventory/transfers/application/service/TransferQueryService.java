package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.in.QueryTransfersUseCase;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort.TransferFilter;
import com.optiplant.inventory.transfers.domain.exception.TransferNotFoundException;
import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferPage;
import com.optiplant.inventory.transfers.domain.model.TransferSummary;
import com.optiplant.inventory.transfers.domain.service.TransferAccessPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side of {@code transfers} (contract §6, R-27): own branch on either side, {@code ADMIN}
 * network-wide (RN-08). {@link TransferRepositoryPort} returns raw branch {@code external_id}s
 * only (design §6.1); this service enriches with branch names in one batch call, the same split
 * {@code StockQueryService} uses for product names (RNF-PER-01, no N+1).
 *
 * <p>{@code @Service} restored in S2 (task 2.7) — see {@code RequestTransferService}'s class
 * Javadoc.
 */
@Service
public class TransferQueryService implements QueryTransfersUseCase {

	private final TransferRepositoryPort transferRepository;
	private final TransferReferencePort referencePort;

	public TransferQueryService(TransferRepositoryPort transferRepository, TransferReferencePort referencePort) {
		this.transferRepository = transferRepository;
		this.referencePort = referencePort;
	}

	@Override
	@Transactional(readOnly = true)
	public TransferPage list(AuthenticatedPrincipal actor, TransferListQuery query) {
		UUID callerBranchExternalId = actor.role() == Role.ADMIN ? null : actor.branchId();

		TransferPage raw = transferRepository.list(new TransferFilter(callerBranchExternalId, query.status(),
				query.direction(), query.from(), query.to(), query.sort(), query.page(), query.size()));

		Set<UUID> branchIds = new HashSet<>();
		for (TransferSummary summary : raw.content()) {
			branchIds.add(summary.originBranch().externalId());
			branchIds.add(summary.destinationBranch().externalId());
		}
		Map<UUID, BranchReference> branches = referencePort.findBranches(branchIds);

		List<TransferSummary> enriched = raw.content().stream()
				.map(summary -> TransferDetailAssembler.toSummary(summary, branches))
				.toList();

		return new TransferPage(enriched, raw.totalElements(), raw.page(), raw.size());
	}

	@Override
	@Transactional(readOnly = true)
	public TransferDetail detail(AuthenticatedPrincipal actor, UUID transferExternalId) {
		Transfer transfer = transferRepository.findByExternalId(transferExternalId)
				.orElseThrow(() -> new TransferNotFoundException(transferExternalId));
		TransferAccessPolicy.assertVisible(actor, transfer);

		return TransferDetailAssembler.toDetail(transfer, referencePort);
	}
}
