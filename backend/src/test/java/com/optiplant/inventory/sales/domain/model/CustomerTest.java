package com.optiplant.inventory.sales.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.CustomerInactiveException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerTest {

	@Test
	@DisplayName("R-C1 / R-C2: Customer creation with valid fields and defaults")
	void createCustomerSuccess() {
		UUID id = UUID.randomUUID();
		Instant now = Instant.now();
		Customer customer = new Customer(
				id,
				new CustomerName("Acme Corp"),
				CustomerTaxId.of("J-12345678-9"),
				new CustomerContact("contact@acme.com", "+58 412 1234567", "Calle 1"),
				true,
				now,
				now
		);

		assertThat(customer.externalId()).isEqualTo(id);
		assertThat(customer.name().value()).isEqualTo("Acme Corp");
		assertThat(customer.taxId().value()).isEqualTo("J-12345678-9");
		assertThat(customer.contact().email()).isEqualTo("contact@acme.com");
		assertThat(customer.contact().phone()).isEqualTo("+58 412 1234567");
		assertThat(customer.contact().address()).isEqualTo("Calle 1");
		assertThat(customer.active()).isTrue();
	}

	@Test
	@DisplayName("R-C7 / D-4: requireActiveForSale passes when active and throws CustomerInactiveException when disabled")
	void requireActiveForSale() {
		Customer activeCustomer = sampleCustomer(true);
		activeCustomer.requireActiveForSale(); // should not throw

		Customer disabledCustomer = sampleCustomer(false);
		assertThatThrownBy(disabledCustomer::requireActiveForSale)
				.isInstanceOf(CustomerInactiveException.class)
				.hasMessageContaining(disabledCustomer.externalId().toString());
	}

	@Test
	@DisplayName("R-C3: disable() transitions active -> false and updates updatedAt")
	void disableCustomer() {
		Customer customer = sampleCustomer(true);
		Instant before = customer.updatedAt();

		Customer disabled = customer.disable();

		assertThat(disabled.active()).isFalse();
		assertThat(disabled.updatedAt()).isAfterOrEqualTo(before);
	}

	@Test
	@DisplayName("R-C3: enable() transitions inactive -> true and updates updatedAt")
	void enableCustomer() {
		Customer customer = sampleCustomer(false);
		Instant before = customer.updatedAt();

		Customer enabled = customer.enable();

		assertThat(enabled.active()).isTrue();
		assertThat(enabled.updatedAt()).isAfterOrEqualTo(before);
	}

	@Test
	@DisplayName("R-C2: withDetails updates name, taxId, contact, and updatedAt")
	void withDetailsUpdatesFields() {
		Customer customer = sampleCustomer(true);
		Instant before = customer.updatedAt();

		Customer updated = customer.withDetails(
				new CustomerName("Updated Name"),
				CustomerTaxId.of("V-98765432-1"),
				new CustomerContact("new@acme.com", "+58 414 0000000", "Av. 2")
		);

		assertThat(updated.name().value()).isEqualTo("Updated Name");
		assertThat(updated.taxId().value()).isEqualTo("V-98765432-1");
		assertThat(updated.contact().email()).isEqualTo("new@acme.com");
		assertThat(updated.updatedAt()).isAfterOrEqualTo(before);
	}

	@Test
	@DisplayName("R-C1: CustomerName rejects null, blank, or > 150 chars")
	void customerNameValidation() {
		assertThatThrownBy(() -> new CustomerName(null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CustomerName(""))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CustomerName("   "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CustomerName("a".repeat(151)))
				.isInstanceOf(IllegalArgumentException.class);

		CustomerName valid = new CustomerName("  Acme Inc.  ");
		assertThat(valid.value()).isEqualTo("Acme Inc.");
	}

	@Test
	@DisplayName("R-C1: CustomerTaxId trims and normalizes blank/null to value null")
	void customerTaxIdValidation() {
		assertThat(CustomerTaxId.of(null).value()).isNull();
		assertThat(CustomerTaxId.of("").value()).isNull();
		assertThat(CustomerTaxId.of("   ").value()).isNull();

		CustomerTaxId taxId = CustomerTaxId.of("  J-12345678-0  ");
		assertThat(taxId).isNotNull();
		assertThat(taxId.value()).isEqualTo("J-12345678-0");

		assertThatThrownBy(() -> CustomerTaxId.of("A".repeat(31)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-C1: CustomerContact validates email, phone, and address limits")
	void customerContactValidation() {
		CustomerContact contact = new CustomerContact("test@example.com", "12345", "Street 1");
		assertThat(contact.email()).isEqualTo("test@example.com");
		assertThat(contact.phone()).isEqualTo("12345");
		assertThat(contact.address()).isEqualTo("Street 1");

		CustomerContact emptyContact = new CustomerContact(null, "  ", null);
		assertThat(emptyContact.email()).isNull();
		assertThat(emptyContact.phone()).isNull();
		assertThat(emptyContact.address()).isNull();

		assertThatThrownBy(() -> new CustomerContact("a".repeat(101), null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CustomerContact("a@b.com", "1".repeat(51), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CustomerContact("a@b.com", null, "a".repeat(256)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private static Customer sampleCustomer(boolean active) {
		Instant now = Instant.now();
		return new Customer(
				UUID.randomUUID(),
				new CustomerName("Sample Customer"),
				CustomerTaxId.of("J-11111111-1"),
				new CustomerContact("sample@test.com", "123456", "Sample Address"),
				active,
				now,
				now
		);
	}
}
