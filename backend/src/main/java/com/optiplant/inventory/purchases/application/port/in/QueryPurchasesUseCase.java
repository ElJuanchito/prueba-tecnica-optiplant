package com.optiplant.inventory.purchases.application.port.in;

import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Primary use case for the purchase history (CU-COM-05, RF-COM-03). Branch scope is the caller's;
 * the branch filter is never a client parameter (R-24, R-25).
 */
public interface QueryPurchasesUseCase {

	PurchasePage<PurchaseOrderSummary> list(AuthenticatedPrincipal actor, PurchaseOrderListQuery query);

	PurchaseOrderDetail detail(AuthenticatedPrincipal actor, UUID externalId);

	PurchasePage<CostHistoryEntry> costHistory(AuthenticatedPrincipal actor, CostHistoryQuery query);

	record PurchaseOrderListQuery(UUID supplierExternalId, UUID productExternalId, PurchaseOrderStatus status,
			Instant from, Instant to, int page, int size, String sort) {
	}

	record CostHistoryQuery(UUID productExternalId, UUID supplierExternalId, Instant from, Instant to, int page,
			int size) {
	}
}
