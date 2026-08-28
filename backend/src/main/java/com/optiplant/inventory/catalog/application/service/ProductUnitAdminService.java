package com.optiplant.inventory.catalog.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optiplant.inventory.catalog.application.port.in.ManageProductUnitsUseCase;
import com.optiplant.inventory.catalog.application.port.out.ProductRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.ProductUnitRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.ProductUnitRepositoryPort.NewUnitRow;
import com.optiplant.inventory.catalog.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.ProductUnitNotFoundException;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import com.optiplant.inventory.catalog.domain.service.ProductUnitPolicy;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates per-product unit administration (CU-INV-02): list, add, replace,
 * delete. Each mutation is one {@code @Transactional} that loads the product,
 * applies {@link ProductUnitPolicy} to assert R-13/R-14 before any SQL, persists
 * through {@link ProductUnitRepositoryPort}, and ends with an
 * {@link AuditWritePort} write in the same transaction ({@code entityName =
 * "product_units"}, {@code branchId = null} — the catalog is corporate, R-15,
 * R-16). Reads take no actor and are {@code readOnly}.
 *
 * <p>The clear-then-set write ordering that keeps {@code
 * uq_product_units_single_default} satisfiable lives in
 * {@code ProductUnitPersistenceAdapter} (design §8.2); this service only decides
 * <em>what</em> the resulting unit list must be.
 */
@Service
public class ProductUnitAdminService implements ManageProductUnitsUseCase {

	private final ProductRepositoryPort productRepository;
	private final ProductUnitRepositoryPort productUnitRepository;
	private final AuditWritePort auditWritePort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	/** Transient id for the validation-only unit handed to the policy; persistence assigns the real one. */
	private static final UUID PLACEHOLDER_ID = new UUID(0L, 0L);

	public ProductUnitAdminService(ProductRepositoryPort productRepository,
			ProductUnitRepositoryPort productUnitRepository, AuditWritePort auditWritePort) {
		this.productRepository = productRepository;
		this.productUnitRepository = productUnitRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ProductUnit> list(UUID productExternalId) {
		requireProduct(productExternalId);
		return productUnitRepository.findByProduct(productExternalId);
	}

	@Override
	@Transactional
	public ProductUnit add(AuthenticatedPrincipal actor, UUID productExternalId, UnitCommand command) {
		Product product = requireProduct(productExternalId);

		ProductUnit incoming = new ProductUnit(PLACEHOLDER_ID, new UnitCode(command.unitName()),
				command.conversionFactor(), command.defaultSaleUnit(), Instant.EPOCH);
		// Building the resulting aggregate is the single place R-13 (duplicate
		// name, base-unit homonym) and R-14 (at most one default) are asserted —
		// done here so a bad payload fails before any SQL is issued (design §8.2).
		ProductUnitPolicy.addUnit(product, incoming);

		ProductUnit created = productUnitRepository.add(productExternalId,
				new NewUnitRow(incoming.unitName().value(), incoming.conversionFactor(), incoming.defaultSaleUnit()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.CREATE.name(), "product_units",
				created.externalId().toString(), null, serialize(created), null));
		return created;
	}

	@Override
	@Transactional
	public ProductUnit replace(AuthenticatedPrincipal actor, UUID productExternalId, UUID unitExternalId,
			UnitCommand command) {
		Product product = requireProduct(productExternalId);
		ProductUnit existing = productUnitRepository.find(productExternalId, unitExternalId)
				.orElseThrow(() -> new ProductUnitNotFoundException(unitExternalId));

		UnitCode name = new UnitCode(command.unitName());
		ProductUnitPolicy.replaceUnit(product, unitExternalId, name, command.conversionFactor(),
				command.defaultSaleUnit());

		ProductUnit updated = productUnitRepository.replace(productExternalId, unitExternalId,
				new NewUnitRow(name.value(), command.conversionFactor(), command.defaultSaleUnit()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.UPDATE.name(), "product_units",
				unitExternalId.toString(), serialize(existing), serialize(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public void delete(AuthenticatedPrincipal actor, UUID productExternalId, UUID unitExternalId) {
		requireProduct(productExternalId);
		ProductUnit existing = productUnitRepository.find(productExternalId, unitExternalId)
				.orElseThrow(() -> new ProductUnitNotFoundException(unitExternalId));

		productUnitRepository.delete(productExternalId, unitExternalId);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.DELETE.name(), "product_units",
				unitExternalId.toString(), serialize(existing), null, null));
	}

	private Product requireProduct(UUID productExternalId) {
		return productRepository.findByExternalId(productExternalId)
				.orElseThrow(() -> new ProductNotFoundException(productExternalId));
	}

	private String serialize(ProductUnit unit) {
		if (unit == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(UnitAuditPayload.from(unit));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record UnitAuditPayload(String unitName, BigDecimal conversionFactor, boolean defaultSaleUnit) {

		static UnitAuditPayload from(ProductUnit unit) {
			return new UnitAuditPayload(unit.unitName().value(), unit.conversionFactor(), unit.defaultSaleUnit());
		}
	}
}
