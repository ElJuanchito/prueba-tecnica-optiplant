package com.optiplant.inventory.iam.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.BranchQuery;
import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.CreateBranchCommand;
import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.EditBranchCommand;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort;
import com.optiplant.inventory.iam.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.iam.domain.exception.DuplicateBranchCodeException;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BranchAdminService} using hand-written in-memory fakes
 * (no Mockito on classpath, mirroring {@link UserAdminServiceTest}).
 */
class BranchAdminServiceTest {

	private FakeBranchRepositoryPort branchRepository;
	private FakeAuditWritePort auditWritePort;
	private BranchAdminService service;
	private AuthenticatedPrincipal admin;

	@BeforeEach
	void setUp() {
		branchRepository = new FakeBranchRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		service = new BranchAdminService(branchRepository, auditWritePort);
		admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN, null);
	}

	@Test
	void creaUnaSucursalConCodigoUnico() {
		BranchProfile created = service.create(admin, new CreateBranchCommand("SUC-BOG", "Sucursal Bogotá",
				"Av. El Dorado #68-90", "Bogotá D.C.", "+57 601 7458900"));

		assertThat(created.externalId()).isNotNull();
		assertThat(created.code()).isEqualTo("SUC-BOG");
		assertThat(created.name()).isEqualTo("Sucursal Bogotá");
		assertThat(created.address()).isEqualTo("Av. El Dorado #68-90");
		assertThat(created.city()).isEqualTo("Bogotá D.C.");
		assertThat(created.phone()).isEqualTo("+57 601 7458900");
		assertThat(created.active()).isTrue();
	}

	@Test
	void rechazaUnCodigoDeSucursalDuplicado() {
		branchRepository.seed(existingBranch("SUC-BOG", "Sucursal Bogotá"));
		CreateBranchCommand command = new CreateBranchCommand("SUC-BOG", "Otra Sucursal", "Calle 100", "Bogotá",
				"12345");

		assertThatThrownBy(() -> service.create(admin, command)).isInstanceOf(DuplicateBranchCodeException.class);
	}

	@Test
	void creaEscribeUnaEntradaDeAuditoriaEnLaMismaTransaccion() {
		BranchProfile created = service.create(admin, new CreateBranchCommand("SUC-MED", "Sucursal Medellín",
				"Autopista Norte km 12", "Medellín", "+57 604 4482310"));

		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("CREATE");
		assertThat(entry.entityName()).isEqualTo("branches");
		assertThat(entry.entityId()).isEqualTo(created.externalId().toString());
		assertThat(entry.branchId()).isEqualTo(created.externalId()); // Branch of affected resource
		assertThat(entry.payloadBefore()).isNull();
		assertThat(entry.payloadAfter()).contains("\"code\":\"SUC-MED\"")
				.contains("\"name\":\"Sucursal Medellín\"")
				.contains("\"address\":\"Autopista Norte km 12\"")
				.contains("\"city\":\"Medellín\"")
				.contains("\"active\":true");
	}

	@Test
	void editaNombreYDireccionDeUnaSucursalExistente() {
		BranchProfile existing = branchRepository.seed(existingBranch("SUC-CAL", "Sucursal Cali"));

		BranchProfile updated = service.edit(admin, existing.externalId(),
				new EditBranchCommand("Sucursal Occidente Cali", "Calle 15 #28-40", "Cali", "+57 602 6691122"));

		assertThat(updated.externalId()).isEqualTo(existing.externalId()); // external_id immutable
		assertThat(updated.code()).isEqualTo("SUC-CAL"); // code unchanged
		assertThat(updated.name()).isEqualTo("Sucursal Occidente Cali");
		assertThat(updated.address()).isEqualTo("Calle 15 #28-40");

		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("UPDATE");
		assertThat(entry.branchId()).isEqualTo(existing.externalId());
		assertThat(entry.payloadBefore()).contains("\"name\":\"Sucursal Cali\"");
		assertThat(entry.payloadAfter()).contains("\"name\":\"Sucursal Occidente Cali\"");
	}

	@Test
	void editarUnExternalIdInexistenteLanzaBranchNotFound() {
		EditBranchCommand command = new EditBranchCommand("Nueva", "Dir", "Ciudad", "123");

		assertThatThrownBy(() -> service.edit(admin, UUID.randomUUID(), command))
				.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void deshabilitarUnaSucursalCambiaActiveAFalsoYRegistraAuditoria() {
		BranchProfile existing = branchRepository.seed(existingBranch("SUC-CAL", "Sucursal Cali"));

		service.disable(admin, existing.externalId());

		assertThat(branchRepository.disabled).contains(existing.externalId());
		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("DISABLE");
		assertThat(entry.branchId()).isEqualTo(existing.externalId());
		assertThat(entry.payloadBefore()).contains("\"active\":true");
		assertThat(entry.payloadAfter()).contains("\"active\":false");
	}

	@Test
	void deshabilitarUnExternalIdInexistenteLanzaBranchNotFound() {
		assertThatThrownBy(() -> service.disable(admin, UUID.randomUUID()))
				.isInstanceOf(BranchNotFoundException.class);
	}

	@Test
	void listarSucursalesConFiltro() {
		BranchProfile b1 = branchRepository.seed(existingBranch("SUC-1", "Sucursal 1"));
		BranchProfile b2 = branchRepository.seed(new BranchProfile(UUID.randomUUID(), "SUC-2", "Sucursal 2", "Dir",
				"Ciudad", "123", false));

		BranchRepositoryPort.BranchPage all = service.list(new BranchQuery(null, 0, 10));
		assertThat(all.content()).hasSize(2);

		BranchRepositoryPort.BranchPage activeOnly = service.list(new BranchQuery(true, 0, 10));
		assertThat(activeOnly.content()).hasSize(1);
		assertThat(activeOnly.content().get(0).code()).isEqualTo("SUC-1");

		BranchRepositoryPort.BranchPage inactiveOnly = service.list(new BranchQuery(false, 0, 10));
		assertThat(inactiveOnly.content()).hasSize(1);
		assertThat(inactiveOnly.content().get(0).code()).isEqualTo("SUC-2");
	}

	private static BranchProfile existingBranch(String code, String name) {
		return new BranchProfile(UUID.randomUUID(), code, name, "Direccion de Prueba", "Ciudad de Prueba", "555-1234",
				true);
	}

	private static final class FakeBranchRepositoryPort implements BranchRepositoryPort {

		private final Map<UUID, BranchProfile> byExternalId = new HashMap<>();
		private final List<UUID> disabled = new ArrayList<>();

		BranchProfile seed(BranchProfile branch) {
			byExternalId.put(branch.externalId(), branch);
			return branch;
		}

		@Override
		public Optional<BranchProfile> findByCode(String code) {
			return byExternalId.values().stream().filter(b -> b.code().equals(code)).findFirst();
		}

		@Override
		public Optional<BranchProfile> findByExternalId(UUID externalId) {
			return Optional.ofNullable(byExternalId.get(externalId));
		}

		@Override
		public BranchProfile create(NewBranch newBranch) {
			BranchProfile created = new BranchProfile(UUID.randomUUID(), newBranch.code(), newBranch.name(),
					newBranch.address(), newBranch.city(), newBranch.phone(), true);
			byExternalId.put(created.externalId(), created);
			return created;
		}

		@Override
		public BranchProfile update(UUID externalId, BranchUpdate update) {
			BranchProfile existing = byExternalId.get(externalId);
			BranchProfile updated = new BranchProfile(existing.externalId(), existing.code(), update.name(),
					update.address(), update.city(), update.phone(), existing.active());
			byExternalId.put(externalId, updated);
			return updated;
		}

		@Override
		public void disable(UUID externalId) {
			disabled.add(externalId);
			BranchProfile existing = byExternalId.get(externalId);
			byExternalId.put(externalId, new BranchProfile(existing.externalId(), existing.code(), existing.name(),
					existing.address(), existing.city(), existing.phone(), false));
		}

		@Override
		public BranchPage list(BranchFilter filter) {
			List<BranchProfile> list = byExternalId.values().stream()
					.filter(b -> filter.active() == null || b.active() == filter.active())
					.toList();
			return new BranchPage(list, list.size(), filter.page(), filter.size());
		}
	}

	private static final class FakeAuditWritePort implements AuditWritePort {

		private final List<AuditEntryCommand> recorded = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			recorded.add(command);
		}
	}
}
