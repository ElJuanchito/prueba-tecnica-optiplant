package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.supplier;

import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort;
import com.optiplant.inventory.purchases.domain.exception.SupplierTaxIdAlreadyExistsException;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter for {@link Supplier} aggregate (design §6.1).
 */
@Component
public class SupplierPersistenceAdapter implements SupplierRepositoryPort {

	private final SupplierSpringDataRepository supplierRepository;

	public SupplierPersistenceAdapter(SupplierSpringDataRepository supplierRepository) {
		this.supplierRepository = supplierRepository;
	}

	@Override
	public Optional<Supplier> findByExternalId(UUID externalId) {
		return supplierRepository.findByExternalId(externalId).map(SupplierMapper::toDomain);
	}

	@Override
	public boolean existsByTaxId(String taxId, UUID excludingExternalId) {
		if (taxId == null || taxId.isBlank()) {
			return false;
		}
		return supplierRepository.existsByTaxId(taxId, excludingExternalId);
	}

	@Override
	public Supplier create(NewSupplier newSupplier) {
		SupplierJpaEntity entity = SupplierMapper.toNewEntity(newSupplier);
		try {
			SupplierJpaEntity saved = supplierRepository.saveAndFlush(entity);
			return SupplierMapper.toDomain(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new SupplierTaxIdAlreadyExistsException();
		}
	}

	@Override
	public Supplier save(Supplier supplier) {
		SupplierJpaEntity entity = supplierRepository.findByExternalId(supplier.externalId())
				.orElseThrow(() -> new IllegalStateException("Supplier not found for external id: " + supplier.externalId()));
		SupplierMapper.updateEntity(entity, supplier);
		try {
			SupplierJpaEntity saved = supplierRepository.saveAndFlush(entity);
			return SupplierMapper.toDomain(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new SupplierTaxIdAlreadyExistsException();
		}
	}

	@Override
	public PurchasePage<Supplier> list(SupplierFilter filter) {
		String search = (filter.search() == null || filter.search().isBlank())
				? null
				: "%" + filter.search().strip() + "%";
		Pageable pageable = PageRequest.of(filter.page(), filter.size());

		Page<SupplierJpaEntity> page;
		if (filter.sort() != null && (filter.sort().equalsIgnoreCase("name,desc") || filter.sort().equalsIgnoreCase("name:desc") || filter.sort().equalsIgnoreCase("-name"))) {
			page = supplierRepository.searchOrderByNameDesc(search, filter.active(), pageable);
		} else if (filter.sort() != null && (filter.sort().equalsIgnoreCase("createdAt,desc") || filter.sort().equalsIgnoreCase("created_at,desc") || filter.sort().equalsIgnoreCase("createdAt:desc") || filter.sort().equalsIgnoreCase("-createdAt"))) {
			page = supplierRepository.searchOrderByCreatedAtDesc(search, filter.active(), pageable);
		} else {
			page = supplierRepository.searchOrderByNameAsc(search, filter.active(), pageable);
		}

		List<Supplier> content = page.getContent().stream()
				.map(SupplierMapper::toDomain)
				.toList();

		return new PurchasePage<>(content, page.getTotalElements(), page.getNumber(), page.getSize());
	}
}
