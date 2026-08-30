package com.optiplant.inventory.pricing.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase.CreatePriceListCommand;
import com.optiplant.inventory.pricing.application.port.in.ManagePriceListsUseCase.UpdatePriceListCommand;
import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.domain.exception.PriceListCodeAlreadyExistsException;
import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import com.optiplant.inventory.pricing.domain.model.PriceListName;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagePriceListsServiceTest {

	@Mock
	private PriceListRepositoryPort priceListRepository;
	@Mock
	private AuditWritePort auditWritePort;

	private ManagePriceListsService service;
	private AuthenticatedPrincipal admin;
	private UUID adminId;

	@BeforeEach
	void setUp() {
		service = new ManagePriceListsService(priceListRepository, auditWritePort);
		adminId = UUID.randomUUID();
		admin = new AuthenticatedPrincipal(adminId, "admin", Role.ADMIN, null);
	}

	@Test
	@DisplayName("R-15 / R-17 / T-03: Create price list persists list and records audit with null branchId")
	void createPriceListPersistsAndAudits() {
		CreatePriceListCommand command = new CreatePriceListCommand("WHOLESALE", "Wholesale List", "Desc", new BigDecimal("15.00"));

		when(priceListRepository.findByCode(new PriceListCode("WHOLESALE"))).thenReturn(Optional.empty());
		when(priceListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PriceList created = service.create(admin, command);

		assertThat(created.code().value()).isEqualTo("WHOLESALE");
		assertThat(created.maxDiscountPercent().value()).isEqualByComparingTo("15.00");
		assertThat(created.active()).isTrue();

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				null, // T-03: null branch for corporate rows
				"CREATE_PRICE_LIST",
				"price_lists",
				created.externalId().toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-15: Create with duplicate code throws PriceListCodeAlreadyExistsException")
	void createDuplicateCodeThrows() {
		CreatePriceListCommand command = new CreatePriceListCommand("EXISTING", "List", null, new BigDecimal("10.00"));
		PriceList existing = new PriceList(UUID.randomUUID(), new PriceListCode("EXISTING"), new PriceListName("List"), null,
				DiscountCap.of("10.00"), false, true, Instant.now(), Instant.now());

		when(priceListRepository.findByCode(new PriceListCode("EXISTING"))).thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service.create(admin, command))
				.isInstanceOf(PriceListCodeAlreadyExistsException.class);
	}

	@Test
	@DisplayName("R-15 / R-17: Update price list updates fields and records audit")
	void updatePriceListUpdatesAndAudits() {
		UUID listId = UUID.randomUUID();
		PriceList existing = new PriceList(listId, new PriceListCode("RETAIL"), new PriceListName("Old Name"), "Old Desc",
				DiscountCap.of("10.00"), false, true, Instant.now(), Instant.now());

		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(existing));
		when(priceListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PriceList updated = service.update(admin, listId, new UpdatePriceListCommand("New Name", "New Desc", new BigDecimal("20.00")));

		assertThat(updated.name().value()).isEqualTo("New Name");
		assertThat(updated.maxDiscountPercent().value()).isEqualByComparingTo("20.00");

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				null,
				"UPDATE_PRICE_LIST",
				"price_lists",
				listId.toString(),
				null,
				null,
				null
		));
	}

	@Test
	@DisplayName("R-15 / R-17: Deactivate price list sets active=false and records audit")
	void deactivatePriceListDeactivatesAndAudits() {
		UUID listId = UUID.randomUUID();
		PriceList existing = new PriceList(listId, new PriceListCode("RETAIL"), new PriceListName("Retail"), null,
				DiscountCap.of("10.00"), false, true, Instant.now(), Instant.now());

		when(priceListRepository.findByExternalId(listId)).thenReturn(Optional.of(existing));
		when(priceListRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PriceList deactivated = service.deactivate(admin, listId);

		assertThat(deactivated.active()).isFalse();

		verify(auditWritePort).record(new AuditEntryCommand(
				adminId,
				null,
				"DEACTIVATE_PRICE_LIST",
				"price_lists",
				listId.toString(),
				null,
				null,
				null
		));
	}
}
