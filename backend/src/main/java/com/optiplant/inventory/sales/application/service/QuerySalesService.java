package com.optiplant.inventory.sales.application.service;

import com.optiplant.inventory.sales.application.port.in.QuerySalesUseCase;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.SaleFilter;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.service.SaleAccessPolicy;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates querying sales, receipts and filtered summaries (CU-VEN-04, HU-VEN-04, design §5).
 *
 * <p>Enforces branch scoping (R-25): {@code ADMIN} can query network-wide; other roles see only
 * sales belonging to their session branch. A sale belonging to another branch is answered with
 * {@link SaleNotFoundException} (404) to prevent enumeration (RNF-SEC-03).
 */
@Service
public class QuerySalesService implements QuerySalesUseCase {

	private final SaleRepositoryPort saleRepository;
	private final SaleReferencePort referencePort;
	private final PriceResolutionPort priceResolutionPort;

	public QuerySalesService(
			SaleRepositoryPort saleRepository,
			SaleReferencePort referencePort,
			PriceResolutionPort priceResolutionPort
	) {
		this.saleRepository = saleRepository;
		this.referencePort = referencePort;
		this.priceResolutionPort = priceResolutionPort;
	}

	@Override
	public SalePage list(AuthenticatedPrincipal actor, SaleListQuery query) {
		UUID callerBranch = actor.role() == Role.ADMIN ? null : actor.branchId();
		return saleRepository.list(new SaleFilter(
				callerBranch,
				query.status(),
				query.from(),
				query.to(),
				query.sort(),
				query.page(),
				query.size()
		));
	}

	@Override
	public SaleDetail detail(AuthenticatedPrincipal actor, UUID externalId) {
		Sale sale = saleRepository.findByExternalId(externalId)
				.orElseThrow(() -> new SaleNotFoundException(externalId));

		SaleAccessPolicy.assertVisible(actor, sale);

		return SaleDetailAssembler.toDetail(sale, referencePort, priceResolutionPort);
	}

	@Override
	public SaleDetail byInvoiceNumber(AuthenticatedPrincipal actor, String invoiceNumber) {
		Sale sale = saleRepository.findByInvoiceNumber(invoiceNumber)
				.orElseThrow(() -> new SaleNotFoundException("Sale not found for invoice number: " + invoiceNumber));

		SaleAccessPolicy.assertVisible(actor, sale);

		return SaleDetailAssembler.toDetail(sale, referencePort, priceResolutionPort);
	}
}
