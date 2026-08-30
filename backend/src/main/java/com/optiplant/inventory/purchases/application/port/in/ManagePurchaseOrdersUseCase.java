package com.optiplant.inventory.purchases.application.port.in;

import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary use case for creating and editing purchase orders (CU-COM-02, RF-COM-01). Editing is
 * allowed only while {@code PENDING} (R-10, PA-07). No command carries a branch id (RN-14).
 */
public interface ManagePurchaseOrdersUseCase {

	PurchaseOrderDetail create(AuthenticatedPrincipal actor, CreatePurchaseOrderCommand command);

	PurchaseOrderDetail edit(AuthenticatedPrincipal actor, UUID externalId, EditPurchaseOrderCommand command);

	record CreatePurchaseOrderCommand(UUID supplierExternalId, String paymentTerms, String notes,
			List<PurchaseOrderLineCommand> items) {
	}

	record EditPurchaseOrderCommand(UUID supplierExternalId, String paymentTerms, String notes,
			List<PurchaseOrderLineCommand> items) {
	}

	record PurchaseOrderLineCommand(UUID productExternalId, BigDecimal quantity, UUID unitOfMeasureExternalId,
			BigDecimal unitCost, BigDecimal discountPercent) {
	}
}
