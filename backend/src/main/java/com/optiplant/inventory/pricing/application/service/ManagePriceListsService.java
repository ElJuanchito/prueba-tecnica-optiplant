package com.optiplant.inventory.pricing.application.service;

import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort.PriceListFilter;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort.PriceListPage;
import com.optiplant.inventory.pricing.domain.exception.PriceListCodeAlreadyExistsException;
import com.optiplant.inventory.pricing.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import com.optiplant.inventory.pricing.domain.model.PriceListName;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orchestrates price list administration (RF-VEN-03, design §3, §5).
 * Writes are audited synchronously in the same transaction (R-17, T-03).
 */
@Service
public class ManagePriceListsService implements ManagePriceListsUseCase {

	private final PriceListRepositoryPort priceListRepository;
	private final AuditWritePort auditWritePort;

	public ManagePriceListsService(PriceListRepositoryPort priceListRepository, AuditWritePort auditWritePort) {
		this.priceListRepository = priceListRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public PriceList create(AuthenticatedPrincipal actor, CreatePriceListCommand command) {
		PriceListCode code = new PriceListCode(command.code());
		if (priceListRepository.findByCode(code).isPresent()) {
			throw new PriceListCodeAlreadyExistsException(code.value());
		}

		PriceListName name = new PriceListName(command.name());
		DiscountCap maxDiscount = new DiscountCap(command.maxDiscountPercent());
		Instant now = Instant.now();

		PriceList priceList = new PriceList(
				UUID.randomUUID(),
				code,
				name,
				command.description(),
				maxDiscount,
				false,
				true,
				now,
				now
		);

		PriceList saved = priceListRepository.save(priceList);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"CREATE_PRICE_LIST",
				"price_lists",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	public PriceList update(AuthenticatedPrincipal actor, UUID externalId, UpdatePriceListCommand command) {
		PriceList existing = priceListRepository.findByExternalId(externalId)
				.orElseThrow(() -> new PriceListNotFoundException(externalId));

		PriceList updated = existing.update(
				new PriceListName(command.name()),
				command.description(),
				new DiscountCap(command.maxDiscountPercent())
		);

		PriceList saved = priceListRepository.save(updated);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"UPDATE_PRICE_LIST",
				"price_lists",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	public PriceList deactivate(AuthenticatedPrincipal actor, UUID externalId) {
		PriceList existing = priceListRepository.findByExternalId(externalId)
				.orElseThrow(() -> new PriceListNotFoundException(externalId));

		PriceList deactivated = existing.deactivate();
		PriceList saved = priceListRepository.save(deactivated);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"DEACTIVATE_PRICE_LIST",
				"price_lists",
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	public PriceListPage list(AuthenticatedPrincipal actor, PriceListQuery query) {
		return priceListRepository.list(new PriceListFilter(query.active(), query.page(), query.size()));
	}

	@Override
	public PriceList get(AuthenticatedPrincipal actor, UUID externalId) {
		return priceListRepository.findByExternalId(externalId)
				.orElseThrow(() -> new PriceListNotFoundException(externalId));
	}
}
