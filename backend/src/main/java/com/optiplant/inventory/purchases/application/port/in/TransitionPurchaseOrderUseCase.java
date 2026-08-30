package com.optiplant.inventory.purchases.application.port.in;

import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for approving or cancelling a purchase order (CU-COM-03, RF-COM-05, RN-15).
 * Both are {@code ADMIN} / {@code BRANCH_MANAGER} only (§5).
 */
public interface TransitionPurchaseOrderUseCase {

	PurchaseOrderDetail approve(AuthenticatedPrincipal actor, UUID externalId);

	PurchaseOrderDetail cancel(AuthenticatedPrincipal actor, UUID externalId, CancelPurchaseOrderCommand command);

	record CancelPurchaseOrderCommand(String reason) {
	}
}
