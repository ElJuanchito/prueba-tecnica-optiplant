package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.in.ManageSuppliersUseCase;
import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort.NewSupplier;
import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort.SupplierFilter;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.SupplierTaxIdAlreadyExistsException;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.purchases.domain.model.SupplierContact;
import com.optiplant.inventory.purchases.domain.model.SupplierName;
import com.optiplant.inventory.purchases.domain.model.SupplierTaxId;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates supplier management (CU-COM-01, RF-COM-06, design §4). Supplier audit entries are
 * corporate — {@code branch_id = null} (T-03) — and use the generic {@link AuditAction} verbs.
 */
@Service
@Transactional
public class ManageSuppliersService implements ManageSuppliersUseCase {

	private static final String ENTITY_NAME = "SUPPLIER";

	private final SupplierRepositoryPort supplierRepository;
	private final AuditWritePort auditWritePort;

	public ManageSuppliersService(SupplierRepositoryPort supplierRepository, AuditWritePort auditWritePort) {
		this.supplierRepository = Objects.requireNonNull(supplierRepository, "supplierRepository must not be null");
		this.auditWritePort = Objects.requireNonNull(auditWritePort, "auditWritePort must not be null");
	}

	@Override
	@Transactional(readOnly = true)
	public PurchasePage<Supplier> list(SupplierQuery query) {
		return supplierRepository.list(new SupplierFilter(
				query.search(), query.active(), query.page(), query.size(), query.sort()));
	}

	@Override
	@Transactional(readOnly = true)
	public Supplier get(UUID externalId) {
		return supplierRepository.findByExternalId(externalId)
				.orElseThrow(() -> new SupplierNotFoundException(externalId));
	}

	@Override
	public Supplier create(AuthenticatedPrincipal actor, CreateSupplierCommand command) {
		SupplierTaxId taxId = new SupplierTaxId(command.taxId());
		SupplierName name = new SupplierName(command.name());
		SupplierContact contact = new SupplierContact(command.contactName(), command.email(), command.phone(),
				command.address());

		if (supplierRepository.existsByTaxId(taxId.value(), null)) {
			throw new SupplierTaxIdAlreadyExistsException();
		}

		Supplier created = supplierRepository.create(new NewSupplier(taxId, name, contact));
		audit(actor, AuditAction.CREATE, created.externalId());
		return created;
	}

	@Override
	public Supplier edit(AuthenticatedPrincipal actor, UUID externalId, EditSupplierCommand command) {
		Supplier existing = supplierRepository.findByExternalId(externalId)
				.orElseThrow(() -> new SupplierNotFoundException(externalId));

		SupplierName name = new SupplierName(command.name());
		SupplierContact contact = new SupplierContact(command.contactName(), command.email(), command.phone(),
				command.address());

		Supplier saved = supplierRepository.save(existing.withDetails(name, contact, Instant.now()));
		audit(actor, AuditAction.UPDATE, saved.externalId());
		return saved;
	}

	@Override
	public Supplier disable(AuthenticatedPrincipal actor, UUID externalId) {
		Supplier existing = supplierRepository.findByExternalId(externalId)
				.orElseThrow(() -> new SupplierNotFoundException(externalId));

		Supplier saved = supplierRepository.save(existing.disable(Instant.now()));
		audit(actor, AuditAction.DISABLE, saved.externalId());
		return saved;
	}

	@Override
	public Supplier enable(AuthenticatedPrincipal actor, UUID externalId) {
		Supplier existing = supplierRepository.findByExternalId(externalId)
				.orElseThrow(() -> new SupplierNotFoundException(externalId));

		Supplier saved = supplierRepository.save(existing.enable(Instant.now()));
		audit(actor, AuditAction.ENABLE, saved.externalId());
		return saved;
	}

	private void audit(AuthenticatedPrincipal actor, AuditAction action, UUID supplierExternalId) {
		auditWritePort.record(new AuditEntryCommand(
				actor.userId(), null, action.name(), ENTITY_NAME, supplierExternalId.toString(), null, null, null));
	}
}
