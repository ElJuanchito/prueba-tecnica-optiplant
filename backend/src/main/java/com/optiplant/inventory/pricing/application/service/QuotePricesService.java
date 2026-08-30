package com.optiplant.inventory.pricing.application.service;

import com.optiplant.inventory.pricing.application.port.in.QuotePricesUseCase;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PricingReferencePort;
import com.optiplant.inventory.pricing.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.service.PriceResolutionPolicy;
import com.optiplant.inventory.pricing.domain.service.QuoteCalculator;
import com.optiplant.inventory.pricing.domain.service.QuoteCalculator.QuoteItemCalculation;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates price quote calculations (CU-VEN-02 preload, design §3, §5).
 */
@Service
@Transactional(readOnly = true)
public class QuotePricesService implements QuotePricesUseCase {

	private final PriceListRepositoryPort priceListRepository;
	private final PriceRepositoryPort priceRepository;
	private final PricingReferencePort referencePort;

	public QuotePricesService(
			PriceListRepositoryPort priceListRepository,
			PriceRepositoryPort priceRepository,
			PricingReferencePort referencePort
	) {
		this.priceListRepository = priceListRepository;
		this.priceRepository = priceRepository;
		this.referencePort = referencePort;
	}

	@Override
	public QuoteResult quote(AuthenticatedPrincipal actor, QuoteCommand command) {
		PriceList list;
		if (command.priceListExternalId() != null) {
			list = priceListRepository.findByExternalId(command.priceListExternalId())
					.filter(PriceList::active)
					.orElseThrow(() -> new PriceListNotFoundException(command.priceListExternalId()));
		} else {
			if (actor.branchId() == null) {
				throw new PriceListNotFoundException("No price list specified and actor has no branch context");
			}
			list = priceListRepository.findActiveDefaultListForBranch(actor.branchId())
					.orElseThrow(() -> new PriceListNotFoundException(
							"No active default price list found for branch " + actor.branchId()));
		}

		List<QuoteItemCommand> items = command.items() == null ? List.of() : command.items();
		List<UUID> productIds = items.stream().map(QuoteItemCommand::productExternalId).toList();
		for (UUID productId : productIds) {
			referencePort.requireActiveProduct(productId);
		}

		LocalDate today = LocalDate.now();
		List<Price> eligiblePrices = priceRepository.findEligible(list.externalId(), actor.branchId(), productIds, today);
		Map<UUID, Price> resolved = PriceResolutionPolicy.resolveAll(eligiblePrices, today);

		List<QuoteItemResult> results = new ArrayList<>();
		for (QuoteItemCommand item : items) {
			Price price = resolved.get(item.productExternalId());
			if (price == null) {
				throw new ProductNotFoundException(
						"No eligible price found for product " + item.productExternalId() + " in list " + list.code().value());
			}
			QuoteItemCalculation calc = QuoteCalculator.calculateLine(
					item.productExternalId(),
					item.quantity(),
					item.discountPercent(),
					price.unitPrice().value(),
					list.maxDiscountPercent()
			);
			results.add(new QuoteItemResult(
					calc.productExternalId(),
					calc.listUnitPrice(),
					calc.unitPrice(),
					calc.subtotal()
			));
		}

		return new QuoteResult(list.externalId(), list.code().value(), list.maxDiscountPercent().value(), results);
	}
}
