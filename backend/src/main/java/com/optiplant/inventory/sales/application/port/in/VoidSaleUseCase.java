package com.optiplant.inventory.sales.application.port.in;

import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for voiding/cancelling a sale (CU-VEN-03, design §5).
 */
public interface VoidSaleUseCase {

	SaleDetail voidSale(AuthenticatedPrincipal actor, UUID saleExternalId, VoidSaleCommand command);

	record VoidSaleCommand(String reason) {
	}
}
