package com.optiplant.inventory.purchases.application.port.in;

import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary use case for registering a goods reception and recalculating the WAC (CU-COM-04,
 * RF-COM-02, RF-COM-04, RN-10). One {@code @Transactional} method (design §5).
 */
public interface ReceivePurchaseUseCase {

	PurchaseOrderDetail receive(AuthenticatedPrincipal actor, UUID orderExternalId, ReceivePurchaseCommand command);

	record ReceivePurchaseCommand(String notes, List<ReceptionItemCommand> items) {
	}

	record ReceptionItemCommand(UUID itemExternalId, BigDecimal receivedQuantity, UUID unitOfMeasureExternalId) {
	}
}
