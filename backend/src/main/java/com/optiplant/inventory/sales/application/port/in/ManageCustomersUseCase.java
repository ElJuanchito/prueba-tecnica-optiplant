package com.optiplant.inventory.sales.application.port.in;

import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for managing customer records (CU-VEN-05, RF-VEN-06, design §3).
 */
public interface ManageCustomersUseCase {

	CustomerPage list(CustomerQuery query);

	Customer get(UUID externalId);

	Customer create(AuthenticatedPrincipal actor, CreateCustomerCommand command);

	Customer edit(AuthenticatedPrincipal actor, UUID externalId, EditCustomerCommand command);

	Customer disable(AuthenticatedPrincipal actor, UUID externalId);

	Customer enable(AuthenticatedPrincipal actor, UUID externalId);

	record CreateCustomerCommand(
			String name,
			String taxId,
			String email,
			String phone,
			String address
	) {
	}

	record EditCustomerCommand(
			String name,
			String taxId,
			String email,
			String phone,
			String address
	) {
	}

	record CustomerQuery(
			String search,
			Boolean active,
			int page,
			int size,
			String sort
	) {
	}
}
