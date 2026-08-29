package com.optiplant.inventory.inventory.application.service;

import com.optiplant.inventory.inventory.application.port.in.QueryStockUseCase;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort.StockFilter;
import com.optiplant.inventory.inventory.application.port.out.ProductLookupPort;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.model.BranchAvailability;
import com.optiplant.inventory.inventory.domain.model.NetworkAvailability;
import com.optiplant.inventory.inventory.domain.model.ProductDescriptor;
import com.optiplant.inventory.inventory.domain.model.StockLine;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import com.optiplant.inventory.inventory.domain.service.BranchScopePolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only stock queries (CU-INV-03, CU-INV-04). {@code branch_inventories} carries no product
 * name, and {@code inventory} declares no {@code @Entity} for {@code catalog}'s table (design
 * §6.1), so {@link #listOwnBranchStock} enriches each balance row with a batch
 * {@link ProductLookupPort} lookup rather than one query per row (RNF-PER-01, no N+1).
 *
 * <p>{@code @Service} restored in S2 (task 2.14) — see {@code StockMovementService}'s class
 * Javadoc for why S1 shipped this unannotated.
 */
@Service
public class StockQueryService implements QueryStockUseCase {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final ProductLookupPort productLookupPort;

	public StockQueryService(BranchInventoryRepositoryPort branchInventoryRepository,
			ProductLookupPort productLookupPort) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.productLookupPort = productLookupPort;
	}

	@Override
	@Transactional(readOnly = true)
	public StockPage listOwnBranchStock(AuthenticatedPrincipal actor, StockQuery query) {
		UUID branchExternalId = BranchScopePolicy.resolveOwnBranch(actor);

		StockPage raw = branchInventoryRepository.list(new StockFilter(branchExternalId, query.productExternalId(),
				query.belowThreshold(), query.sort(), query.page(), query.size()));

		List<UUID> productIds = raw.content().stream().map(StockLine::productExternalId).distinct().toList();
		Map<UUID, ProductDescriptor> descriptors = productLookupPort.findAllByExternalIds(productIds);

		List<StockLine> enriched = raw.content().stream()
				.map(line -> enrich(line, descriptors.get(line.productExternalId())))
				.toList();
		return new StockPage(enriched, raw.totalElements(), raw.page(), raw.size());
	}

	@Override
	@Transactional(readOnly = true)
	public NetworkAvailability networkAvailability(AuthenticatedPrincipal actor, UUID productExternalId) {
		ProductDescriptor product = productLookupPort.findByExternalId(productExternalId)
				.orElseThrow(() -> new ProductNotFoundException(productExternalId));

		List<BranchAvailability> raw = branchInventoryRepository.findAcrossActiveBranches(productExternalId);
		UUID ownBranch = actor.isCorporate() ? null : actor.branchId();

		List<BranchAvailability> marked = raw.stream().map(branch -> mark(branch, ownBranch)).toList();
		BigDecimal networkTotal = marked.stream().map(BranchAvailability::currentStock)
				.reduce(BigDecimal.ZERO, BigDecimal::add);

		return new NetworkAvailability(product.externalId(), product.sku(), product.name(), marked, networkTotal);
	}

	private static BranchAvailability mark(BranchAvailability branch, UUID ownBranch) {
		Boolean isOwnBranch = ownBranch == null ? null : ownBranch.equals(branch.branchExternalId());
		return new BranchAvailability(branch.branchExternalId(), branch.branchName(), branch.currentStock(),
				branch.reservedStock(), branch.inTransitStock(), branch.availableStock(), isOwnBranch);
	}

	private static StockLine enrich(StockLine line, ProductDescriptor descriptor) {
		if (descriptor == null) {
			return line;
		}
		return new StockLine(line.productExternalId(), descriptor.sku(), descriptor.name(), line.currentStock(),
				line.reservedStock(), line.inTransitStock(), line.availableStock(), line.minStockThreshold(),
				line.averageCost(), line.lastUpdatedAt());
	}
}
