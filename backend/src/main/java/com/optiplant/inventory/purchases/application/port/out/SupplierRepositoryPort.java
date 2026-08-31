package com.optiplant.inventory.purchases.application.port.out;

import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.purchases.domain.model.SupplierContact;
import com.optiplant.inventory.purchases.domain.model.SupplierName;
import com.optiplant.inventory.purchases.domain.model.SupplierTaxId;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for supplier persistence (design §4). Suppliers are corporate — no branch scope
 * (R-02). No adapter until S2; the S1 application service that depends on it ships unannotated
 * (design §10 trap 4).
 */
public interface SupplierRepositoryPort {

	Optional<Supplier> findByExternalId(UUID externalId);

	/** {@code true} when {@code taxId} is stored on a supplier other than {@code excludingExternalId} (R-01). */
	boolean existsByTaxId(String taxId, UUID excludingExternalId);

	Supplier create(NewSupplier newSupplier);

	Supplier save(Supplier supplier);

	PurchasePage<Supplier> list(SupplierFilter filter);

	record NewSupplier(SupplierTaxId taxId, SupplierName name, SupplierContact contact) {
	}

	record SupplierFilter(String search, Boolean active, int page, int size, String sort) {
	}
}
