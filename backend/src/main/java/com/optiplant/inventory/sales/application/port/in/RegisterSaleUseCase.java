package com.optiplant.inventory.sales.application.port.in;

import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary use case for registering a commercial sale (CU-VEN-01, CU-EXT-02, design §5).
 * The command carries an optional {@code invoiceNumber} (supplied by external POS, null for internal path).
 */
public interface RegisterSaleUseCase {

	SaleDetail register(AuthenticatedPrincipal actor, RegisterSaleCommand command);

	record RegisterSaleCommand(
			UUID priceListExternalId,
			String customerName,
			String customerTaxId,
			BigDecimal taxPercent,
			String notes,
			List<RegisterSaleItemCommand> items,
			String invoiceNumber
	) {
	}

	record RegisterSaleItemCommand(
			UUID productExternalId,
			BigDecimal quantity,
			UUID unitOfMeasureExternalId,
			BigDecimal discountPercent
	) {
	}
}
