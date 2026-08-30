package com.optiplant.inventory.sales.domain.model;

import com.optiplant.inventory.sales.domain.exception.CustomerInactiveException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Customer aggregate record inside the sales module (D-1, R-C1..R-C7, design §2).
 */
public record Customer(
		UUID externalId,
		CustomerName name,
		CustomerTaxId taxId,
		CustomerContact contact,
		boolean active,
		Instant createdAt,
		Instant updatedAt
) {

	public Customer {
		Objects.requireNonNull(name, "name must not be null");
		if (contact == null) {
			contact = CustomerContact.empty();
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}

	public static Customer create(UUID externalId, CustomerName name, CustomerTaxId taxId, CustomerContact contact, Instant now) {
		Instant timestamp = now != null ? now : Instant.now();
		return new Customer(
				externalId != null ? externalId : UUID.randomUUID(),
				name,
				taxId,
				contact != null ? contact : CustomerContact.empty(),
				true,
				timestamp,
				timestamp
		);
	}

	public Customer withDetails(CustomerName newName, CustomerTaxId newTaxId, CustomerContact newContact, Instant now) {
		return new Customer(
				this.externalId,
				newName != null ? newName : this.name,
				newTaxId,
				newContact != null ? newContact : CustomerContact.empty(),
				this.active,
				this.createdAt,
				now != null ? now : Instant.now()
		);
	}

	public Customer withDetails(CustomerName newName, CustomerTaxId newTaxId, CustomerContact newContact) {
		return withDetails(newName, newTaxId, newContact, Instant.now());
	}

	public Customer disable(Instant now) {
		return new Customer(
				this.externalId,
				this.name,
				this.taxId,
				this.contact,
				false,
				this.createdAt,
				now != null ? now : Instant.now()
		);
	}

	public Customer disable() {
		return disable(Instant.now());
	}

	public Customer enable(Instant now) {
		return new Customer(
				this.externalId,
				this.name,
				this.taxId,
				this.contact,
				true,
				this.createdAt,
				now != null ? now : Instant.now()
		);
	}

	public Customer enable() {
		return enable(Instant.now());
	}

	public void requireActiveForSale() {
		if (!this.active) {
			throw new CustomerInactiveException(this.externalId);
		}
	}
}
