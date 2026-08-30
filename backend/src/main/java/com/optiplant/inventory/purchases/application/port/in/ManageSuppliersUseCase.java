package com.optiplant.inventory.purchases.application.port.in;

import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Primary use case for managing suppliers (CU-COM-01, RF-COM-06). Writes are {@code ADMIN}-only,
 * reads open to any authenticated user (§5, PA-06).
 */
public interface ManageSuppliersUseCase {

	PurchasePage<Supplier> list(SupplierQuery query);

	Supplier get(UUID externalId);

	Supplier create(AuthenticatedPrincipal actor, CreateSupplierCommand command);

	Supplier edit(AuthenticatedPrincipal actor, UUID externalId, EditSupplierCommand command);

	Supplier disable(AuthenticatedPrincipal actor, UUID externalId);

	Supplier enable(AuthenticatedPrincipal actor, UUID externalId);

	record CreateSupplierCommand(String taxId, String name, String contactName, String email, String phone,
			String address) {
	}

	record EditSupplierCommand(String name, String contactName, String email, String phone, String address) {
	}

	record SupplierQuery(String search, Boolean active, int page, int size, String sort) {
	}
}
