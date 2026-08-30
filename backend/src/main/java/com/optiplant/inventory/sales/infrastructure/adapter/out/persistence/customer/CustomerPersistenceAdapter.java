package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.customer;

import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort;
import com.optiplant.inventory.sales.domain.exception.CustomerTaxIdAlreadyExistsException;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter for Customer aggregate (design §6).
 */
@Component
public class CustomerPersistenceAdapter implements CustomerRepositoryPort {

	private final CustomerSpringDataRepository customerRepository;

	public CustomerPersistenceAdapter(CustomerSpringDataRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	@Override
	public Optional<Customer> findByExternalId(UUID externalId) {
		return customerRepository.findByExternalId(externalId).map(CustomerMapper::toDomain);
	}

	@Override
	public boolean existsByTaxId(String taxId, UUID excludingExternalId) {
		if (taxId == null || taxId.isBlank()) {
			return false;
		}
		return customerRepository.existsByTaxId(taxId, excludingExternalId);
	}

	@Override
	public Customer create(NewCustomer newCustomer) {
		CustomerJpaEntity entity = CustomerMapper.toNewEntity(newCustomer);
		try {
			CustomerJpaEntity saved = customerRepository.saveAndFlush(entity);
			return CustomerMapper.toDomain(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new CustomerTaxIdAlreadyExistsException();
		}
	}

	@Override
	public Customer save(Customer customer) {
		CustomerJpaEntity entity = customerRepository.findByExternalId(customer.externalId())
				.orElseThrow(() -> new IllegalStateException("Customer not found for external id: " + customer.externalId()));
		CustomerMapper.updateEntity(entity, customer);
		try {
			CustomerJpaEntity saved = customerRepository.saveAndFlush(entity);
			return CustomerMapper.toDomain(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new CustomerTaxIdAlreadyExistsException();
		}
	}

	@Override
	public CustomerPage list(CustomerFilter filter) {
		String search = (filter.search() == null || filter.search().isBlank())
				? null
				: "%" + filter.search().strip() + "%";
		Pageable pageable = PageRequest.of(filter.page(), filter.size());

		Page<CustomerJpaEntity> page;
		if (filter.sort() != null && (filter.sort().equalsIgnoreCase("name,desc") || filter.sort().equalsIgnoreCase("name:desc") || filter.sort().equalsIgnoreCase("-name"))) {
			page = customerRepository.searchOrderByNameDesc(search, filter.active(), pageable);
		} else if (filter.sort() != null && (filter.sort().equalsIgnoreCase("createdAt,desc") || filter.sort().equalsIgnoreCase("created_at,desc") || filter.sort().equalsIgnoreCase("createdAt:desc") || filter.sort().equalsIgnoreCase("-createdAt"))) {
			page = customerRepository.searchOrderByCreatedAtDesc(search, filter.active(), pageable);
		} else {
			page = customerRepository.searchOrderByNameAsc(search, filter.active(), pageable);
		}

		List<Customer> content = page.getContent().stream()
				.map(CustomerMapper::toDomain)
				.toList();

		return new CustomerPage(content, page.getTotalElements(), page.getNumber(), page.getSize());
	}
}
