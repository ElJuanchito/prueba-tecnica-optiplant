package com.optiplant.inventory.pricing.application.service;

import com.optiplant.inventory.pricing.application.port.in.ManagePricesUseCase;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort.PriceFilter;
import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort.PricePage;
import com.optiplant.inventory.pricing.application.port.out.PricingReferencePort;
import com.optiplant.inventory.pricing.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.PriceNotFoundException;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import com.optiplant.inventory.pricing.domain.service.PriceSupersessionPolicy;
import com.optiplant.inventory.pricing.domain.service.PriceSupersessionPolicy.SupersessionPlan;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates price entry management and supersession (R-15, R-16, design §3, §5).
 * Writes are audited synchronously in the same transaction (R-17, T-03).
 */
@Service
public class ManagePricesService implements ManagePricesUseCase {

	private final PriceListRepositoryPort priceListRepository;
	private final PriceRepositoryPort priceRepository;
	private final PricingReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public ManagePricesService(
			PriceListRepositoryPort priceListRepository,
			PriceRepositoryPort priceRepository,
			PricingReferencePort referencePort,
			AuditWritePort auditWritePort
	) {
		this.priceListRepository = priceListRepository;
		this.priceRepository = priceRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public Price setPrice(AuthenticatedPrincipal actor, UUID priceListExternalId, SetPriceCommand command) {
		PriceList priceList = priceListRepository.findByExternalId(priceListExternalId)
				.orElseThrow(() -> new PriceListNotFoundException(priceListExternalId));

		referencePort.requireActiveProduct(command.productExternalId());
		if (command.branchExternalId() != null) {
			referencePort.requireActiveBranch(command.branchExternalId());
		}

		LocalDate validFrom = command.validFrom() != null ? command.validFrom() : LocalDate.now();
		Price proposed = new Price(
				UUID.randomUUID(),
				priceList.externalId(),
				command.productExternalId(),
				command.branchExternalId(),
				new UnitPrice(command.unitPrice()),
				ValidityRange.open(validFrom),
				Instant.now()
		);

		List<Price> openPrices = priceRepository.findOpen(
				priceList.externalId(),
				command.productExternalId(),
				command.branchExternalId()
		);

		SupersessionPlan plan = PriceSupersessionPolicy.plan(openPrices, proposed);
		if (plan.closedPrice().isPresent()) {
			priceRepository.save(plan.closedPrice().get());
		}
		Price saved = priceRepository.save(plan.newPrice());

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				command.branchExternalId(),
				"SET_PRICE",
				"price_list_items",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	public Price closePrice(AuthenticatedPrincipal actor, UUID priceExternalId, ClosePriceCommand command) {
		Price existing = priceRepository.findByExternalId(priceExternalId)
				.orElseThrow(() -> new PriceNotFoundException(priceExternalId));

		LocalDate validTo = command.validTo() != null ? command.validTo() : LocalDate.now();
		Price closed = existing.close(validTo);
		Price saved = priceRepository.save(closed);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				saved.branchExternalId(),
				"CLOSE_PRICE",
				"price_list_items",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	public PricePage listPrices(AuthenticatedPrincipal actor, UUID priceListExternalId, PriceQuery query) {
		priceListRepository.findByExternalId(priceListExternalId)
				.orElseThrow(() -> new PriceListNotFoundException(priceListExternalId));

		return priceRepository.list(new PriceFilter(
				priceListExternalId,
				query.productExternalId(),
				query.branchExternalId(),
				query.currentOnly(),
				query.page(),
				query.size()
		));
	}
}
