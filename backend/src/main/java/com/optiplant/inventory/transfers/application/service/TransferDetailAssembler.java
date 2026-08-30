package com.optiplant.inventory.transfers.application.service;

import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.ProductReference;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferItemView;
import com.optiplant.inventory.transfers.domain.model.TransferSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enriches a {@link Transfer} (branch {@code external_id}s and product {@code external_id}s
 * only, per design §6.1) with the branch names and product {@code sku}/{@code name} the API
 * surface requires (contract §6), batching lookups through {@link TransferReferencePort} so no
 * detail or page issues one query per row (RNF-PER-01) — the same split
 * {@code StockQueryService} uses between {@code BranchInventoryRepositoryPort} and
 * {@code ProductLookupPort}.
 */
final class TransferDetailAssembler {

	private TransferDetailAssembler() {
	}

	static TransferDetail toDetail(Transfer transfer, TransferReferencePort referencePort) {
		Map<UUID, BranchReference> branches = referencePort
				.findBranches(Set.of(transfer.originBranchExternalId(), transfer.destinationBranchExternalId()));
		Map<UUID, ProductReference> products = referencePort.findProducts(
				transfer.items().stream().map(TransferItem::productExternalId).collect(java.util.stream.Collectors.toSet()));

		List<TransferItemView> itemViews = transfer.items().stream()
				.map(item -> toItemView(item, products.get(item.productExternalId())))
				.toList();

		List<String> enrichedObservations = transfer.notes().observations().stream()
				.map(obs -> enrichObservation(obs, products))
				.toList();

		return new TransferDetail(transfer.externalId(), transfer.number(), transfer.status(),
				transfer.notes().priority(), branchRef(transfer.originBranchExternalId(), branches),
				branchRef(transfer.destinationBranchExternalId(), branches), transfer.carrierName(),
				transfer.trackingNumber(), transfer.dispatchedAt(), transfer.estimatedArrivalAt(),
				transfer.actualArrivalAt(), transfer.deviationHours().orElse(null), enrichedObservations,
				transfer.requestedByUserExternalId(), transfer.dispatchedByUserExternalId(),
				transfer.receivedByUserExternalId(), transfer.createdAt(), transfer.updatedAt(), itemViews);
	}

	static TransferSummary toSummary(TransferSummary raw, Map<UUID, BranchReference> branches) {
		return new TransferSummary(raw.externalId(), raw.number(), raw.status(), raw.priority(),
				branchRef(raw.originBranch().externalId(), branches), branchRef(raw.destinationBranch().externalId(), branches),
				raw.createdAt(), raw.estimatedArrivalAt());
	}

	private static String enrichObservation(String observation, Map<UUID, ProductReference> products) {
		if (observation == null || observation.isBlank()) {
			return observation;
		}
		String enriched = observation;
		for (Map.Entry<UUID, ProductReference> entry : products.entrySet()) {
			String uuidStr = entry.getKey().toString();
			if (enriched.contains(uuidStr)) {
				ProductReference ref = entry.getValue();
				String displayName = ref != null && ref.name() != null ? ref.name() : uuidStr;
				enriched = enriched.replace("Item " + uuidStr, displayName);
				enriched = enriched.replace(uuidStr, displayName);
			}
		}
		return enriched;
	}

	private static TransferItemView toItemView(TransferItem item, ProductReference product) {
		String sku = product == null ? null : product.sku();
		String name = product == null ? null : product.name();
		return new TransferItemView(item.externalId(), item.productExternalId(), sku, name,
				item.requestedQuantity().value(), item.dispatchedQuantity().value(), item.receivedQuantity().value(),
				item.discrepancyQuantity().value(), item.discrepancyReason());
	}

	private static BranchReference branchRef(UUID branchExternalId, Map<UUID, BranchReference> branches) {
		BranchReference found = branches.get(branchExternalId);
		return found != null ? found : new BranchReference(branchExternalId, null);
	}
}
