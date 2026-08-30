package com.optiplant.inventory.sales.application.service;

import com.optiplant.inventory.sales.application.port.in.RegisterSaleUseCase;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSale;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSaleItem;
import com.optiplant.inventory.sales.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.sales.domain.exception.PriceListNotResolvableException;
import com.optiplant.inventory.sales.domain.exception.PriceNotAvailableException;
import com.optiplant.inventory.sales.domain.exception.UnitConversionUnavailableException;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.sales.domain.model.TaxPercent;
import com.optiplant.inventory.sales.domain.service.DiscountCapPolicy;
import com.optiplant.inventory.sales.domain.service.SaleAccessPolicy;
import com.optiplant.inventory.sales.domain.service.SaleBasketPolicy;
import com.optiplant.inventory.sales.domain.service.SaleBasketPolicy.RawBasketItem;
import com.optiplant.inventory.sales.domain.service.SalePricingPolicy;
import com.optiplant.inventory.sales.domain.service.SalePricingPolicy.PricedLine;
import com.optiplant.inventory.sales.domain.service.UnitConversionPolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.price.AppliedPriceList;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.stock.StockMovementType;
import com.optiplant.inventory.shared.stock.StockMutationCommand;
import com.optiplant.inventory.shared.stock.StockMutationPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates sale registration (CU-VEN-01, CU-EXT-02, design §5, §7, §11).
 *
 * <p>Execution pipeline:
 * <ol>
 *   <li>Access check &amp; branch resolution (R-02).</li>
 *   <li>Basket validation &amp; sorting into lock order (R-01, R-06, T-02).</li>
 *   <li>Product active validation.</li>
 *   <li>Unit of measure conversion to base units (R-07, RN-13).</li>
 *   <li>Applied price list resolution (R-10).</li>
 *   <li>Unit price resolution in a single batch (R-11, RN-16, RNF-PER-02).</li>
 *   <li>Discount cap validation (R-13, RN-17).</li>
 *   <li>Line pricing &amp; totals calculation (R-14).</li>
 *   <li>Persistence of sale and sale items (T-01).</li>
 *   <li>Stock decrement via {@link StockMutationPort} in lock order without supplied cost (P-01, P-03, R-03).</li>
 *   <li>Audit log recording on the sale's branch (T-03).</li>
 * </ol>
 */
@Service
public class RegisterSaleService implements RegisterSaleUseCase {

	private final SaleRepositoryPort saleRepository;
	private final SaleReferencePort referencePort;
	private final PriceResolutionPort priceResolutionPort;
	private final StockMutationPort stockMutationPort;
	private final AuditWritePort auditWritePort;

	public RegisterSaleService(
			SaleRepositoryPort saleRepository,
			SaleReferencePort referencePort,
			PriceResolutionPort priceResolutionPort,
			StockMutationPort stockMutationPort,
			AuditWritePort auditWritePort
	) {
		this.saleRepository = saleRepository;
		this.referencePort = referencePort;
		this.priceResolutionPort = priceResolutionPort;
		this.stockMutationPort = stockMutationPort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public SaleDetail register(AuthenticatedPrincipal actor, RegisterSaleCommand command) {
		UUID branchExternalId = SaleAccessPolicy.resolveRegistrationBranch(actor);

		List<RegisterSaleItemCommand> commandItems = command.items() == null ? List.of() : command.items();
		List<RawBasketItem> rawLines = commandItems.stream()
				.map(item -> new RawBasketItem(
						item.productExternalId(),
						item.quantity(),
						item.unitOfMeasureExternalId(),
						item.discountPercent()
				))
				.toList();

		List<RawBasketItem> sortedLines = SaleBasketPolicy.validateAndSort(rawLines);

		for (RawBasketItem line : sortedLines) {
			referencePort.requireActiveProduct(line.productExternalId());
		}

		List<SaleQuantity> baseQuantities = new ArrayList<>();
		for (RawBasketItem line : sortedLines) {
			BigDecimal factor = null;
			if (line.unitOfMeasureExternalId() != null) {
				factor = referencePort.findConversionFactor(line.productExternalId(), line.unitOfMeasureExternalId())
						.orElseThrow(() -> new UnitConversionUnavailableException(
								line.productExternalId(),
								line.unitOfMeasureExternalId()
						));
			}
			SaleQuantity baseQty = UnitConversionPolicy.toBaseUnit(
					line.productExternalId(),
					line.unitOfMeasureExternalId(),
					line.quantity(),
					factor
			);
			baseQuantities.add(baseQty);
		}

		AppliedPriceList appliedList;
		if (command.priceListExternalId() != null) {
			appliedList = priceResolutionPort.findActiveListByExternalId(command.priceListExternalId())
					.orElseThrow(() -> new PriceListNotFoundException(command.priceListExternalId()));
		} else {
			appliedList = priceResolutionPort.findActiveDefaultListForBranch(branchExternalId)
					.orElseThrow(() -> new PriceListNotResolvableException(
							"No active default price list found for branch " + branchExternalId));
		}

		List<UUID> productIds = sortedLines.stream().map(RawBasketItem::productExternalId).toList();
		LocalDate today = LocalDate.now();
		Map<UUID, BigDecimal> resolvedPrices = priceResolutionPort.resolveUnitPrices(
				appliedList.externalId(),
				branchExternalId,
				productIds,
				today
		);

		List<PricedLine> pricedLines = new ArrayList<>();
		for (int i = 0; i < sortedLines.size(); i++) {
			RawBasketItem line = sortedLines.get(i);
			SaleQuantity baseQty = baseQuantities.get(i);

			BigDecimal listPriceVal = resolvedPrices.get(line.productExternalId());
			if (listPriceVal == null) {
				throw new PriceNotAvailableException(line.productExternalId());
			}

			DiscountCapPolicy.validate(line.discountPercent(), appliedList.maxDiscountPercent());

			DiscountPercent discount = line.discountPercent() != null
					? new DiscountPercent(line.discountPercent())
					: DiscountPercent.ZERO;

			PricedLine priced = SalePricingPolicy.priceLine(
					line.productExternalId(),
					baseQty,
					new Money(listPriceVal),
					discount
			);
			pricedLines.add(priced);
		}

		TaxPercent tax = command.taxPercent() != null ? new TaxPercent(command.taxPercent()) : TaxPercent.ZERO;
		SaleTotals totals = SalePricingPolicy.calculateTotals(pricedLines, tax);

		InvoiceNumber invoiceNumber = command.invoiceNumber() != null ? new InvoiceNumber(command.invoiceNumber()) : null;
		SaleNotes notes = command.notes() != null ? SaleNotes.fromHumanNote(command.notes()) : SaleNotes.empty();

		List<NewSaleItem> newItems = pricedLines.stream()
				.map(p -> new NewSaleItem(
						p.productExternalId(),
						p.quantity(),
						p.listUnitPrice(),
						p.unitPrice(),
						p.discountPercent(),
						p.subtotal()
				))
				.toList();

		NewSale newSale = new NewSale(
				invoiceNumber,
				branchExternalId,
				actor.userId(),
				appliedList.externalId(),
				new CustomerName(command.customerName()),
				CustomerTaxId.of(command.customerTaxId()),
				totals,
				notes,
				newItems
		);

		Sale created = saleRepository.create(newSale);

		for (PricedLine line : pricedLines) {
			StockMutationCommand mutation = new StockMutationCommand(
					branchExternalId,
					line.productExternalId(),
					StockMovementType.SALE,
					line.quantity().value(),
					null,
					"SALE_INVOICE",
					created.externalId().toString(),
					null,
					actor.userId()
			);
			stockMutationPort.applyMovement(mutation);
		}

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				branchExternalId,
				"REGISTER_SALE",
				"sales",
				created.externalId().toString(),
				null,
				null,
				null
		));

		return SaleDetailAssembler.toDetail(created, referencePort, priceResolutionPort);
	}
}
