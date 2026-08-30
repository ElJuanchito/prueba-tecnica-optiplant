package com.optiplant.inventory.pricing.application.port.in;

import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Primary use case for calculating price quotes (CU-VEN-02 preload, design §5).
 * Open to any authenticated role.
 */
public interface QuotePricesUseCase {

	QuoteResult quote(AuthenticatedPrincipal actor, QuoteCommand command);

	record QuoteCommand(UUID priceListExternalId, List<QuoteItemCommand> items) {
	}

	record QuoteItemCommand(UUID productExternalId, BigDecimal quantity, BigDecimal discountPercent) {
	}

	record QuoteResult(UUID priceListExternalId, String code, BigDecimal maxDiscountPercent, List<QuoteItemResult> items) {
	}

	record QuoteItemResult(UUID productExternalId, BigDecimal listUnitPrice, BigDecimal unitPrice, BigDecimal subtotal) {
	}
}
