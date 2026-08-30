package com.optiplant.inventory.sales.application.service;

import com.optiplant.inventory.sales.application.port.in.VoidSaleUseCase;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.model.CancellationReason;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.service.SaleAccessPolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.OutboundValuationPort;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates voiding/cancelling a sale (CU-VEN-03, design §5, §7).
 *
 * <p>Execution pipeline:
 * <ol>
 *   <li>Acquires pessimistic write lock on the {@code sales} row (T-02, F-7).</li>
 *   <li>Checks access &amp; visibility (R-22, R-25, §5).</li>
 *   <li>Validates state transition via {@link com.optiplant.inventory.sales.domain.service.SaleStateMachine} (R-18).</li>
 *   <li>Reads original unit costs via {@link OutboundValuationPort} (F-1, F-2, R-19).</li>
 *   <li>Reinstates inventory balances via {@link StockMutationPort} with {@code ADJUSTMENT_POS} (R-19, R-20, R-21).</li>
 *   <li>Persists status change to {@code CANCELLED} and reasons in notes (F-3).</li>
 *   <li>Records audit entry on the sale's branch (T-03).</li>
 * </ol>
 *
 * <p>Unannotated in S1 (task 1.11, design §12 trap 2); {@code @Service} is restored in S2.
 */
public class VoidSaleService implements VoidSaleUseCase {

	private final SaleRepositoryPort saleRepository;
	private final SaleReferencePort referencePort;
	private final PriceResolutionPort priceResolutionPort;
	private final OutboundValuationPort outboundValuationPort;
	private final StockMutationPort stockMutationPort;
	private final AuditWritePort auditWritePort;

	public VoidSaleService(
			SaleRepositoryPort saleRepository,
			SaleReferencePort referencePort,
			PriceResolutionPort priceResolutionPort,
			OutboundValuationPort outboundValuationPort,
			StockMutationPort stockMutationPort,
			AuditWritePort auditWritePort
	) {
		this.saleRepository = saleRepository;
		this.referencePort = referencePort;
		this.priceResolutionPort = priceResolutionPort;
		this.outboundValuationPort = outboundValuationPort;
		this.stockMutationPort = stockMutationPort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public SaleDetail voidSale(AuthenticatedPrincipal actor, UUID saleExternalId, VoidSaleCommand command) {
		Sale sale = saleRepository.lockForUpdate(saleExternalId)
				.orElseThrow(() -> new SaleNotFoundException(saleExternalId));

		SaleAccessPolicy.assertCanVoid(actor, sale);

		CancellationReason reason = new CancellationReason(command.reason());
		Sale cancelled = sale.cancel(reason);

		Map<UUID, BigDecimal> originalCosts = outboundValuationPort.outboundUnitCosts(
				sale.branchExternalId(),
				"SALE_INVOICE",
				sale.externalId().toString()
		);

		for (SaleItem item : sale.items()) {
			BigDecimal unitCost = originalCosts.get(item.productExternalId());
			StockMutationCommand reversal = new StockMutationCommand(
					sale.branchExternalId(),
					item.productExternalId(),
					StockMovementType.ADJUSTMENT_POS,
					item.quantity().value(),
					unitCost,
					"SALE_VOID",
					sale.externalId().toString(),
					null,
					actor.userId()
			);
			stockMutationPort.applyMovement(reversal);
		}

		Sale saved = saleRepository.save(cancelled);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				saved.branchExternalId(),
				"VOID_SALE",
				"sales",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return SaleDetailAssembler.toDetail(saved, referencePort, priceResolutionPort);
	}
}
