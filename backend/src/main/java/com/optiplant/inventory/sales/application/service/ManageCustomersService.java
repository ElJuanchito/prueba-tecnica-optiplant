package com.optiplant.inventory.sales.application.service;

import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase;
import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort;
import com.optiplant.inventory.sales.domain.exception.CustomerNotFoundException;
import com.optiplant.inventory.sales.domain.exception.CustomerTaxIdAlreadyExistsException;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerContact;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates customer management operations (CU-VEN-05, RF-VEN-06, design §3).
 */
@Service
public class ManageCustomersService implements ManageCustomersUseCase {

	private static final String ENTITY_NAME = "CUSTOMER";

	private final CustomerRepositoryPort customerRepository;
	private final AuditWritePort auditWritePort;

	public ManageCustomersService(CustomerRepositoryPort customerRepository, AuditWritePort auditWritePort) {
		this.customerRepository = Objects.requireNonNull(customerRepository, "customerRepository must not be null");
		this.auditWritePort = Objects.requireNonNull(auditWritePort, "auditWritePort must not be null");
	}

	@Override
	@Transactional(readOnly = true)
	public CustomerPage list(CustomerQuery query) {
		return customerRepository.list(new CustomerRepositoryPort.CustomerFilter(
				query.search(),
				query.active(),
				query.page(),
				query.size(),
				query.sort()
		));
	}

	@Override
	@Transactional(readOnly = true)
	public Customer get(UUID externalId) {
		return customerRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CustomerNotFoundException(externalId));
	}

	@Override
	@Transactional
	public Customer create(AuthenticatedPrincipal actor, CreateCustomerCommand command) {
		CustomerName name = new CustomerName(command.name());
		CustomerTaxId taxId = new CustomerTaxId(command.taxId());
		CustomerContact contact = new CustomerContact(command.email(), command.phone(), command.address());

		if (taxId.value() != null && customerRepository.existsByTaxId(taxId.value(), null)) {
			throw new CustomerTaxIdAlreadyExistsException();
		}

		Customer created = customerRepository.create(new CustomerRepositoryPort.NewCustomer(name, taxId, contact));

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"CREATE_CUSTOMER",
				ENTITY_NAME,
				created.externalId().toString(),
				null,
				null,
				null
		));

		return created;
	}

	@Override
	@Transactional
	public Customer edit(AuthenticatedPrincipal actor, UUID externalId, EditCustomerCommand command) {
		Customer existing = customerRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CustomerNotFoundException(externalId));

		CustomerName name = new CustomerName(command.name());
		CustomerTaxId taxId = new CustomerTaxId(command.taxId());
		CustomerContact contact = new CustomerContact(command.email(), command.phone(), command.address());

		if (taxId.value() != null && customerRepository.existsByTaxId(taxId.value(), existing.externalId())) {
			throw new CustomerTaxIdAlreadyExistsException();
		}

		Customer updated = existing.withDetails(name, taxId, contact);
		Customer saved = customerRepository.save(updated);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"UPDATE_CUSTOMER",
				ENTITY_NAME,
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	@Transactional
	public Customer disable(AuthenticatedPrincipal actor, UUID externalId) {
		Customer existing = customerRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CustomerNotFoundException(externalId));

		Customer disabled = existing.disable();
		Customer saved = customerRepository.save(disabled);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"DISABLE_CUSTOMER",
				ENTITY_NAME,
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}

	@Override
	@Transactional
	public Customer enable(AuthenticatedPrincipal actor, UUID externalId) {
		Customer existing = customerRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CustomerNotFoundException(externalId));

		Customer enabled = existing.enable();
		Customer saved = customerRepository.save(enabled);

		auditWritePort.record(new AuditEntryCommand(
				actor.userId(),
				null,
				"ENABLE_CUSTOMER",
				ENTITY_NAME,
				saved.externalId().toString(),
				null,
				null,
				null
		));

		return saved;
	}
}
