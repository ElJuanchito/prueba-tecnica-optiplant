package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.in.QueryPurchasesUseCase;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.CostHistoryFilter;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.PurchaseOrderFilter;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.service.PurchaseAccessPolicy;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the purchase history (CU-COM-05, RF-COM-03, design §4, §6.3). Branch scope is the
 * caller's — {@code ADMIN} network-wide, everyone else their own branch (R-25, D-6); an order of
 * another branch answers {@code 404}, never {@code 403}.
 */
@Service
@Transactional(readOnly = true)
public class QueryPurchasesService implements QueryPurchasesUseCase {

	private final PurchaseOrderRepositoryPort orderRepository;
	private final PurchaseReferencePort referencePort;

	public QueryPurchasesService(PurchaseOrderRepositoryPort orderRepository, PurchaseReferencePort referencePort) {
		this.orderRepository = orderRepository;
		this.referencePort = referencePort;
	}

	@Override
	public PurchasePage<PurchaseOrderSummary> list(AuthenticatedPrincipal actor, PurchaseOrderListQuery query) {
		UUID branchScope = PurchaseAccessPolicy.listingBranchScope(actor);
		return orderRepository.list(new PurchaseOrderFilter(branchScope, query.supplierExternalId(),
				query.productExternalId(), query.status(), query.from(), query.to(), query.sort(),
				query.page(), query.size()));
	}

	@Override
	public PurchaseOrderDetail detail(AuthenticatedPrincipal actor, UUID externalId) {
		PurchaseOrder order = orderRepository.findByExternalId(externalId)
				.orElseThrow(() -> new PurchaseOrderNotFoundException(externalId));
		PurchaseAccessPolicy.assertVisible(actor, order);
		return PurchaseOrderDetailAssembler.toDetail(order, referencePort);
	}

	@Override
	public PurchasePage<CostHistoryEntry> costHistory(AuthenticatedPrincipal actor, CostHistoryQuery query) {
		UUID branchScope = PurchaseAccessPolicy.listingBranchScope(actor);
		return orderRepository.costHistory(new CostHistoryFilter(branchScope, query.productExternalId(),
				query.supplierExternalId(), query.from(), query.to(), query.page(), query.size()));
	}
}
