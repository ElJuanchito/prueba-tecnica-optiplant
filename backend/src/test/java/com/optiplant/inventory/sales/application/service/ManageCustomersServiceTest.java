package com.optiplant.inventory.sales.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.CreateCustomerCommand;
import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.CustomerQuery;
import com.optiplant.inventory.sales.application.port.in.ManageCustomersUseCase.EditCustomerCommand;
import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort;
import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort.CustomerFilter;
import com.optiplant.inventory.sales.application.port.out.CustomerRepositoryPort.NewCustomer;
import com.optiplant.inventory.sales.domain.exception.CustomerNotFoundException;
import com.optiplant.inventory.sales.domain.exception.CustomerTaxIdAlreadyExistsException;
import com.optiplant.inventory.sales.domain.model.Customer;
import com.optiplant.inventory.sales.domain.model.CustomerContact;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerPage;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManageCustomersServiceTest {

	@Mock
	private CustomerRepositoryPort customerRepository;
	@Mock
	private AuditWritePort auditWritePort;

	private ManageCustomersService service;
	private AuthenticatedPrincipal adminActor;
	private UUID actorId;

	@BeforeEach
	void setUp() {
		service = new ManageCustomersService(customerRepository, auditWritePort);
		actorId = UUID.randomUUID();
		adminActor = new AuthenticatedPrincipal(actorId, "admin", Role.ADMIN, null);
	}

	@Test
	@DisplayName("R-C1 / T-C3: Create customer with unique taxId saves and writes audit log with branchId = null")
	void createCustomerSuccess() {
		CreateCustomerCommand command = new CreateCustomerCommand(
				"Acme Corp",
				"J-12345678-0",
				"acme@test.com",
				"123456",
				"Main St"
		);

		when(customerRepository.existsByTaxId("J-12345678-0", null)).thenReturn(false);

		UUID createdId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer created = new Customer(
				createdId,
				new CustomerName("Acme Corp"),
				CustomerTaxId.of("J-12345678-0"),
				new CustomerContact("acme@test.com", "123456", "Main St"),
				true,
				now,
				now
		);
		when(customerRepository.create(any(NewCustomer.class))).thenReturn(created);

		Customer result = service.create(adminActor, command);

		assertThat(result).isNotNull();
		assertThat(result.externalId()).isEqualTo(createdId);

		ArgumentCaptor<NewCustomer> newCustomerCaptor = ArgumentCaptor.forClass(NewCustomer.class);
		verify(customerRepository).create(newCustomerCaptor.capture());
		assertThat(newCustomerCaptor.getValue().name().value()).isEqualTo("Acme Corp");
		assertThat(newCustomerCaptor.getValue().taxId().value()).isEqualTo("J-12345678-0");

		verify(auditWritePort).record(new AuditEntryCommand(
				actorId,
				null,
				"CREATE_CUSTOMER",
				"CUSTOMER",
				createdId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-C1: Create customer with duplicate taxId throws CustomerTaxIdAlreadyExistsException")
	void createCustomerDuplicateTaxIdThrows() {
		CreateCustomerCommand command = new CreateCustomerCommand(
				"Acme Corp",
				"J-12345678-0",
				null,
				null,
				null
		);

		when(customerRepository.existsByTaxId("J-12345678-0", null)).thenReturn(true);

		assertThatThrownBy(() -> service.create(adminActor, command))
				.isInstanceOf(CustomerTaxIdAlreadyExistsException.class);

		verify(customerRepository, never()).create(any());
		verify(auditWritePort, never()).record(any());
	}

	@Test
	@DisplayName("R-C14: Get customer not found throws CustomerNotFoundException")
	void getCustomerNotFoundThrows() {
		UUID id = UUID.randomUUID();
		when(customerRepository.findByExternalId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(id))
				.isInstanceOf(CustomerNotFoundException.class)
				.hasMessageContaining(id.toString());
	}

	@Test
	@DisplayName("R-C2: Edit customer updates fields and records audit")
	void editCustomerSuccess() {
		UUID customerId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer existing = new Customer(
				customerId,
				new CustomerName("Old Name"),
				CustomerTaxId.of("J-11111111-1"),
				null,
				true,
				now,
				now
		);

		when(customerRepository.findByExternalId(customerId)).thenReturn(Optional.of(existing));
		when(customerRepository.existsByTaxId("J-22222222-2", customerId)).thenReturn(false);
		when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		EditCustomerCommand editCommand = new EditCustomerCommand(
				"New Name",
				"J-22222222-2",
				"new@test.com",
				"555",
				"New Address"
		);

		Customer result = service.edit(adminActor, customerId, editCommand);

		assertThat(result.name().value()).isEqualTo("New Name");
		assertThat(result.taxId().value()).isEqualTo("J-22222222-2");

		verify(auditWritePort).record(new AuditEntryCommand(
				actorId,
				null,
				"UPDATE_CUSTOMER",
				"CUSTOMER",
				customerId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-C2: Edit customer with same taxId succeeds")
	void editCustomerSameTaxIdSucceeds() {
		UUID customerId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer existing = new Customer(
				customerId,
				new CustomerName("Old Name"),
				CustomerTaxId.of("J-11111111-1"),
				null,
				true,
				now,
				now
		);

		when(customerRepository.findByExternalId(customerId)).thenReturn(Optional.of(existing));
		when(customerRepository.existsByTaxId("J-11111111-1", customerId)).thenReturn(false);
		when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		EditCustomerCommand editCommand = new EditCustomerCommand(
				"New Name",
				"J-11111111-1",
				null,
				null,
				null
		);

		Customer result = service.edit(adminActor, customerId, editCommand);
		assertThat(result.name().value()).isEqualTo("New Name");
		verify(customerRepository).existsByTaxId("J-11111111-1", customerId);
	}

	@Test
	@DisplayName("R-C3: Disable and Enable customer update state and record audit")
	void disableAndEnableCustomer() {
		UUID customerId = UUID.randomUUID();
		Instant now = Instant.now();
		Customer active = new Customer(
				customerId,
				new CustomerName("Acme"),
				null,
				null,
				true,
				now,
				now
		);

		when(customerRepository.findByExternalId(customerId)).thenReturn(Optional.of(active));
		when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Customer disabled = service.disable(adminActor, customerId);
		assertThat(disabled.active()).isFalse();
		verify(auditWritePort).record(new AuditEntryCommand(
				actorId,
				null,
				"DISABLE_CUSTOMER",
				"CUSTOMER",
				customerId.toString(),
				null,
				null,
				null
		));

		when(customerRepository.findByExternalId(customerId)).thenReturn(Optional.of(disabled));
		Customer enabled = service.enable(adminActor, customerId);
		assertThat(enabled.active()).isTrue();
		verify(auditWritePort).record(new AuditEntryCommand(
				actorId,
				null,
				"ENABLE_CUSTOMER",
				"CUSTOMER",
				customerId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-C0 / R-C5: List customers forwards filter to repository")
	void listCustomersForwardsFilter() {
		CustomerQuery query = new CustomerQuery("Acme", true, 0, 20, "name");
		when(customerRepository.list(any())).thenReturn(new CustomerPage(List.of(), 0, 0, 20));

		CustomerPage result = service.list(query);

		assertThat(result).isNotNull();
		ArgumentCaptor<CustomerFilter> captor = ArgumentCaptor.forClass(CustomerFilter.class);
		verify(customerRepository).list(captor.capture());
		assertThat(captor.getValue().search()).isEqualTo("Acme");
		assertThat(captor.getValue().active()).isTrue();
		assertThat(captor.getValue().page()).isEqualTo(0);
		assertThat(captor.getValue().size()).isEqualTo(20);
		assertThat(captor.getValue().sort()).isEqualTo("name");
	}
}
