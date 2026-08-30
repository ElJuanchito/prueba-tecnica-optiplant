package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.customer;

import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerContact;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps between Customer domain models and JPA entities (design §6).
 */
public final class CustomerMapper {

	private CustomerMapper() {
	}

	public static Customer toDomain(CustomerJpaEntity entity) {
		if (entity == null) {
			return null;
		}
		return new Customer(
				entity.getExternalId(),
				new CustomerName(entity.getName()),
				entity.getTaxId() != null ? new CustomerTaxId(entity.getTaxId()) : null,
				new CustomerContact(entity.getEmail(), entity.getPhone(), entity.getAddress()),
				entity.isActive(),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
		);
	}

	public static CustomerJpaEntity toNewEntity(CustomerRepositoryPort.NewCustomer newCustomer) {
		CustomerJpaEntity entity = new CustomerJpaEntity();
		entity.setExternalId(UUID.randomUUID());
		entity.setName(newCustomer.name().value());
		entity.setTaxId(newCustomer.taxId() != null ? newCustomer.taxId().value() : null);
		if (newCustomer.contact() != null) {
			entity.setEmail(newCustomer.contact().email());
			entity.setPhone(newCustomer.contact().phone());
			entity.setAddress(newCustomer.contact().address());
		}
		entity.setActive(true);
		Instant now = Instant.now();
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return entity;
	}

	public static void updateEntity(CustomerJpaEntity entity, Customer domain) {
		entity.setName(domain.name().value());
		entity.setTaxId(domain.taxId() != null ? domain.taxId().value() : null);
		if (domain.contact() != null) {
			entity.setEmail(domain.contact().email());
			entity.setPhone(domain.contact().phone());
			entity.setAddress(domain.contact().address());
		} else {
			entity.setEmail(null);
			entity.setPhone(null);
			entity.setAddress(null);
		}
		entity.setActive(domain.active());
		entity.setUpdatedAt(domain.updatedAt() != null ? domain.updatedAt() : Instant.now());
	}
}
