package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.supplier;

import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.purchases.domain.model.SupplierContact;
import com.optiplant.inventory.purchases.domain.model.SupplierName;
import com.optiplant.inventory.purchases.domain.model.SupplierTaxId;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps between Supplier domain models and JPA entities (design §6.1).
 */
public final class SupplierMapper {

	private SupplierMapper() {
	}

	public static Supplier toDomain(SupplierJpaEntity entity) {
		if (entity == null) {
			return null;
		}
		return new Supplier(
				entity.getExternalId(),
				new SupplierTaxId(entity.getTaxId()),
				new SupplierName(entity.getName()),
				new SupplierContact(entity.getContactName(), entity.getEmail(), entity.getPhone(), entity.getAddress()),
				entity.isActive(),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
		);
	}

	public static SupplierJpaEntity toNewEntity(SupplierRepositoryPort.NewSupplier newSupplier) {
		SupplierJpaEntity entity = new SupplierJpaEntity();
		entity.setExternalId(UUID.randomUUID());
		entity.setTaxId(newSupplier.taxId().value());
		entity.setName(newSupplier.name().value());
		if (newSupplier.contact() != null) {
			entity.setContactName(newSupplier.contact().contactName());
			entity.setEmail(newSupplier.contact().email());
			entity.setPhone(newSupplier.contact().phone());
			entity.setAddress(newSupplier.contact().address());
		}
		entity.setActive(true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return entity;
	}

	public static void updateEntity(SupplierJpaEntity entity, Supplier domain) {
		entity.setName(domain.name().value());
		entity.setTaxId(domain.taxId().value());
		if (domain.contact() != null) {
			entity.setContactName(domain.contact().contactName());
			entity.setEmail(domain.contact().email());
			entity.setPhone(domain.contact().phone());
			entity.setAddress(domain.contact().address());
		} else {
			entity.setContactName(null);
			entity.setEmail(null);
			entity.setPhone(null);
			entity.setAddress(null);
		}
		entity.setActive(domain.active());
		entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : Instant.now());
	}
}
