package com.optiplant.inventory.sales.application.port.out;

import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerContact;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for customer persistence (design §3, §6).
 */
public interface CustomerRepositoryPort {

	Optional<Customer> findByExternalId(UUID externalId);

	boolean existsByTaxId(String taxId, UUID excludingExternalId);

	Customer create(NewCustomer newCustomer);

	Customer save(Customer customer);

	CustomerPage list(CustomerFilter filter);

	record NewCustomer(
			CustomerName name,
			CustomerTaxId taxId,
			CustomerContact contact
	) {
	}

	record CustomerFilter(
			String search,
			Boolean active,
			int page,
			int size,
			String sort
	) {
	}
}
