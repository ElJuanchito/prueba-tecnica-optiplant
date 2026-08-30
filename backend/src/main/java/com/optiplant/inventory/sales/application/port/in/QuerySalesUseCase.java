package com.optiplant.inventory.sales.application.port.in;

import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Primary use case for querying sales, receipts and listings (CU-VEN-04, HU-VEN-04, design §5).
 */
public interface QuerySalesUseCase {

	SalePage list(AuthenticatedPrincipal actor, SaleListQuery query);

	SaleDetail detail(AuthenticatedPrincipal actor, UUID externalId);

	SaleDetail byInvoiceNumber(AuthenticatedPrincipal actor, String invoiceNumber);

	record SaleListQuery(
			SaleStatus status,
			Instant from,
			Instant to,
			int page,
			int size,
			String sort,
			UUID customerExternalId
	) {
		public SaleListQuery(SaleStatus status, Instant from, Instant to, int page, int size, String sort) {
			this(status, from, to, page, size, sort, null);
		}
	}
}
