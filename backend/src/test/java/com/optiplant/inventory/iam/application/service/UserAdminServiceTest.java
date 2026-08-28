package com.optiplant.inventory.iam.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.CreateUserCommand;
import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.EditUserCommand;
import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.UserQuery;
import com.optiplant.inventory.iam.application.port.out.PasswordHasherPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException;
import com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException;
import com.optiplant.inventory.iam.domain.exception.UserNotFoundException;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import com.optiplant.inventory.iam.domain.model.UserAccount;
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
 * No Mockito on this classpath (verified: {@code ./mvnw dependency:tree}
 * resolves no {@code org.mockito} artifact) — hand-written in-memory fakes,
 * the same style {@code LoginRateLimitTest}/{@code RefreshTokenPolicyTest}
 * use for their own dependencies, exercise the role/branch validation matrix
 * and duplicate rejection (task 5a.6). {@code UserAdminIT} covers the
 * disable-revokes-every-live-token scenario end to end against real Postgres.
 */
class UserAdminServiceTest {

	private static final UUID SUCURSAL_BOGOTA = UUID.randomUUID();
	private static final UUID SUCURSAL_MEDELLIN = UUID.randomUUID();

	private FakeUserRepositoryPort userRepository;
	private FakeRefreshTokenRepositoryPort refreshTokenRepository;
	private FakeAuditWritePort auditWritePort;
	private UserAdminService service;
	private AuthenticatedPrincipal admin;
	private AuthenticatedPrincipal gerenteBogota;

	@BeforeEach
	void setUp() {
		userRepository = new FakeUserRepositoryPort();
		refreshTokenRepository = new FakeRefreshTokenRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		service = new UserAdminService(userRepository, new FakePasswordHasherPort(), refreshTokenRepository,
				auditWritePort);
		admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN, null);
		gerenteBogota = new AuthenticatedPrincipal(UUID.randomUUID(), "gerente.bogota", Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA);
	}

	@Test
	void creaUnAdminCorporativoSinSucursal() {
		UserAccount created = service.create(admin,
				new CreateUserCommand("nuevo.admin", "nuevo.admin@optiplant.com", "Password123!", "Nuevo Admin",
						Role.ADMIN, null));

		assertThat(created.branchExternalId()).isNull();
		assertThat(created.role()).isEqualTo(Role.ADMIN);
		assertThat(created.active()).isTrue();
	}

	@Test
	void creaUnGerenteDeSucursalConSucursalAsignada() {
		UserAccount created = service.create(admin, new CreateUserCommand("nuevo.gerente",
				"nuevo.gerente@optiplant.com", "Password123!", "Nuevo Gerente", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA));

		assertThat(created.branchExternalId()).isEqualTo(SUCURSAL_BOGOTA);
	}

	@Test
	void rechazaUnGerenteDeSucursalSinSucursal() {
		CreateUserCommand command = new CreateUserCommand("sin.sucursal", "sin.sucursal@optiplant.com",
				"Password123!", "Sin Sucursal", Role.BRANCH_MANAGER, null);

		assertThatThrownBy(() -> service.create(admin, command)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnOperadorSinSucursal() {
		CreateUserCommand command = new CreateUserCommand("operador.sin.sucursal",
				"operador.sin.sucursal@optiplant.com", "Password123!", "Operador Sin Sucursal", Role.OPERATOR, null);

		assertThatThrownBy(() -> service.create(admin, command)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rechazaUnUsernameDuplicado() {
		userRepository.seed(existingUser("gerente.bogota", "gerente.bog@optiplant.com", Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA));
		CreateUserCommand command = new CreateUserCommand("gerente.bogota", "otro.email@optiplant.com",
				"Password123!", "Otro Nombre", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.create(admin, command)).isInstanceOf(DuplicateUsernameException.class);
	}

	@Test
	void rechazaUnEmailDuplicado() {
		userRepository.seed(existingUser("gerente.bogota", "gerente.bog@optiplant.com", Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA));
		CreateUserCommand command = new CreateUserCommand("otro.username", "gerente.bog@optiplant.com",
				"Password123!", "Otro Nombre", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.create(admin, command)).isInstanceOf(DuplicateUsernameException.class);
	}

	@Test
	void creaEscribeUnaEntradaDeAuditoriaEnLaMismaTransaccion() {
		service.create(admin, new CreateUserCommand("nuevo.operador", "nuevo.operador@optiplant.com", "Password123!",
				"Nuevo Operador", Role.OPERATOR, SUCURSAL_BOGOTA));

		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("CREATE");
		assertThat(entry.entityName()).isEqualTo("users");
		assertThat(entry.payloadBefore()).isNull();
		assertThat(entry.payloadAfter()).contains("\"role\":\"OPERATOR\"")
				.contains("\"email\":\"nuevo.operador@optiplant.com\"")
				.contains("\"fullName\":\"Nuevo Operador\"")
				.contains("\"active\":true")
				.contains(SUCURSAL_BOGOTA.toString())
				.doesNotContain("Password123!")
				.doesNotContain("hashed");
	}

	@Test
	void unGerenteDeSucursalCreaUnOperadorEnSuPropiaSucursal() {
		UserAccount created = service.create(gerenteBogota, new CreateUserCommand("nuevo.operador",
				"nuevo.operador@optiplant.com", "Password123!", "Nuevo Operador", Role.OPERATOR, SUCURSAL_BOGOTA));

		assertThat(created.role()).isEqualTo(Role.OPERATOR);
		assertThat(created.branchExternalId()).isEqualTo(SUCURSAL_BOGOTA);
	}

	@Test
	void unGerenteDeSucursalNoPuedeCrearUnOperadorEnOtraSucursal() {
		CreateUserCommand command = new CreateUserCommand("operador.medellin", "operador.medellin@optiplant.com",
				"Password123!", "Operador Medellín", Role.OPERATOR, SUCURSAL_MEDELLIN);

		assertThatThrownBy(() -> service.create(gerenteBogota, command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalNoPuedeCrearOtroGerenteDeSucursal() {
		CreateUserCommand command = new CreateUserCommand("otro.gerente", "otro.gerente@optiplant.com",
				"Password123!", "Otro Gerente", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.create(gerenteBogota, command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalEditaUnOperadorDeSuPropiaSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));

		UserAccount updated = service.edit(gerenteBogota, existing.externalId(),
				new EditUserCommand(existing.email(), "Nombre Actualizado", Role.OPERATOR, SUCURSAL_BOGOTA));

		assertThat(updated.fullName()).isEqualTo("Nombre Actualizado");
	}

	@Test
	void unGerenteDeSucursalNoPuedeEditarUnOperadorDeOtraSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.medellin", "operador.med@optiplant.com", Role.OPERATOR, SUCURSAL_MEDELLIN));
		EditUserCommand command = new EditUserCommand(existing.email(), existing.fullName(), Role.OPERATOR,
				SUCURSAL_MEDELLIN);

		assertThatThrownBy(() -> service.edit(gerenteBogota, existing.externalId(), command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalNoPuedeEditarAOtroGerenteDeSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("otro.gerente", "otro.gerente@optiplant.com", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA));
		EditUserCommand command = new EditUserCommand(existing.email(), existing.fullName(), Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.edit(gerenteBogota, existing.externalId(), command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalNoPuedeAscenderUnOperadorAGerenteDeSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));
		EditUserCommand command = new EditUserCommand(existing.email(), existing.fullName(), Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.edit(gerenteBogota, existing.externalId(), command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalNoPuedeMoverUnOperadorAOtraSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));
		EditUserCommand command = new EditUserCommand(existing.email(), existing.fullName(), Role.OPERATOR,
				SUCURSAL_MEDELLIN);

		assertThatThrownBy(() -> service.edit(gerenteBogota, existing.externalId(), command))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalDeshabilitaUnOperadorDeSuPropiaSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));

		service.disable(gerenteBogota, existing.externalId());

		assertThat(userRepository.disabled).contains(existing.externalId());
	}

	@Test
	void unGerenteDeSucursalNoPuedeDeshabilitarUnOperadorDeOtraSucursal() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.medellin", "operador.med@optiplant.com", Role.OPERATOR, SUCURSAL_MEDELLIN));

		assertThatThrownBy(() -> service.disable(gerenteBogota, existing.externalId()))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void laConsultaDeUnGerenteDeSucursalQuedaForzadaASuPropiaSucursalYARolOperador() {
		userRepository.seed(existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR,
				SUCURSAL_BOGOTA));
		userRepository.seed(existingUser("operador.medellin", "operador.med@optiplant.com", Role.OPERATOR,
				SUCURSAL_MEDELLIN));
		userRepository.seed(existingUser("otro.gerente.bogota", "otro.gerente.bog@optiplant.com", Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA));

		UserPage result = service.list(gerenteBogota, new UserQuery(null, Role.ADMIN, SUCURSAL_MEDELLIN, 0, 20));

		assertThat(result.content()).extracting(UserAccount::username).containsExactly("operador.bogota");
	}

	@Test
	void editaElRolYLaSucursalDeUnUsuarioExistente() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));

		UserAccount updated = service.edit(admin, existing.externalId(),
				new EditUserCommand(existing.email(), existing.fullName(), Role.BRANCH_MANAGER, SUCURSAL_BOGOTA));

		assertThat(updated.role()).isEqualTo(Role.BRANCH_MANAGER);
		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("UPDATE");
		assertThat(entry.payloadBefore()).contains("\"role\":\"OPERATOR\"").contains("\"active\":true");
		assertThat(entry.payloadAfter()).contains("\"role\":\"BRANCH_MANAGER\"").contains("\"active\":true");
	}

	@Test
	void editarUnExternalIdInexistenteLanzaUserNotFound() {
		EditUserCommand command = new EditUserCommand("no.existe@optiplant.com", "Nadie", Role.ADMIN, null);

		assertThatThrownBy(() -> service.edit(admin, UUID.randomUUID(), command))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void editarConElPropioEmailDelUsuarioNoSeRechazaComoDuplicado() {
		UserAccount existing = userRepository.seed(
				existingUser("gerente.bogota", "gerente.bog@optiplant.com", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA));

		UserAccount updated = service.edit(admin, existing.externalId(),
				new EditUserCommand(existing.email(), "Nombre Actualizado", Role.BRANCH_MANAGER, SUCURSAL_BOGOTA));

		assertThat(updated.fullName()).isEqualTo("Nombre Actualizado");
	}

	@Test
	void editarConElEmailDeOtroUsuarioSeRechazaComoDuplicado() {
		userRepository.seed(existingUser("gerente.bogota", "gerente.bog@optiplant.com", Role.BRANCH_MANAGER,
				SUCURSAL_BOGOTA));
		UserAccount objetivo = userRepository
				.seed(existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));
		EditUserCommand command = new EditUserCommand("gerente.bog@optiplant.com", objetivo.fullName(), Role.OPERATOR,
				SUCURSAL_BOGOTA);

		assertThatThrownBy(() -> service.edit(admin, objetivo.externalId(), command))
				.isInstanceOf(DuplicateUsernameException.class);
	}

	@Test
	void deshabilitarRevocaTodosLosTokensVivosYRegistraAuditoria() {
		UserAccount existing = userRepository.seed(
				existingUser("operador.bogota", "operador.bog@optiplant.com", Role.OPERATOR, SUCURSAL_BOGOTA));

		service.disable(admin, existing.externalId());

		assertThat(userRepository.disabled).contains(existing.externalId());
		assertThat(refreshTokenRepository.revokedForUser).containsEntry(existing.externalId(),
				RevocationReason.USER_DISABLED);
		assertThat(auditWritePort.recorded).hasSize(1);
		AuditEntryCommand entry = auditWritePort.recorded.get(0);
		assertThat(entry.action()).isEqualTo("DISABLE");
		assertThat(entry.payloadBefore()).contains("\"active\":true");
		assertThat(entry.payloadAfter()).contains("\"active\":false");
	}

	@Test
	void deshabilitarUnExternalIdInexistenteLanzaUserNotFound() {
		assertThatThrownBy(() -> service.disable(admin, UUID.randomUUID())).isInstanceOf(UserNotFoundException.class);
	}

	private static UserAccount existingUser(String username, String email, Role role, UUID branchExternalId) {
		return new UserAccount(UUID.randomUUID(), username, email, "hashed-password", "Nombre de Prueba", role,
				branchExternalId, true);
	}

	/** Minimal in-memory fake, keyed by {@code external_id} — enough to exercise
	 * {@code UserAdminService} without a database or Mockito. */
	private static final class FakeUserRepositoryPort implements UserRepositoryPort {

		private final Map<UUID, UserAccount> byExternalId = new HashMap<>();
		private final List<UUID> disabled = new ArrayList<>();

		UserAccount seed(UserAccount account) {
			byExternalId.put(account.externalId(), account);
			return account;
		}

		@Override
		public Optional<UserAccount> findByUsername(String username) {
			return byExternalId.values().stream().filter(u -> u.username().equals(username)).findFirst();
		}

		@Override
		public Optional<UserAccount> findByExternalId(UUID externalId) {
			return Optional.ofNullable(byExternalId.get(externalId));
		}

		@Override
		public Optional<UserAccount> findByEmail(String email) {
			return byExternalId.values().stream().filter(u -> u.email().equals(email)).findFirst();
		}

		@Override
		public UserAccount create(NewUser newUser) {
			UserAccount created = new UserAccount(UUID.randomUUID(), newUser.username(), newUser.email(),
					newUser.passwordHash(), newUser.fullName(), newUser.role(), newUser.branchExternalId(), true);
			byExternalId.put(created.externalId(), created);
			return created;
		}

		@Override
		public UserAccount update(UUID externalId, UserUpdate update) {
			UserAccount existing = byExternalId.get(externalId);
			UserAccount updated = new UserAccount(existing.externalId(), existing.username(), update.email(),
					existing.passwordHash(), update.fullName(), update.role(), update.branchExternalId(),
					existing.active());
			byExternalId.put(externalId, updated);
			return updated;
		}

		@Override
		public void disable(UUID externalId) {
			disabled.add(externalId);
			UserAccount existing = byExternalId.get(externalId);
			byExternalId.put(externalId, new UserAccount(existing.externalId(), existing.username(),
					existing.email(), existing.passwordHash(), existing.fullName(), existing.role(),
					existing.branchExternalId(), false));
		}

		@Override
		public UserPage list(UserFilter filter) {
			List<UserAccount> content = byExternalId.values().stream()
					.filter(u -> filter.active() == null || filter.active().equals(u.active()))
					.filter(u -> filter.role() == null || filter.role().equals(u.role()))
					.filter(u -> filter.branchExternalId() == null || filter.branchExternalId().equals(u.branchExternalId()))
					.toList();
			return new UserPage(content, content.size(), filter.page(), filter.size());
		}
	}

	private static final class FakeRefreshTokenRepositoryPort implements RefreshTokenRepositoryPort {

		private final Map<UUID, RevocationReason> revokedForUser = new HashMap<>();

		@Override
		public void persist(NewRefreshToken newRefreshToken) {
			// Not exercised by UserAdminServiceTest.
		}

		@Override
		public Optional<com.optiplant.inventory.iam.domain.model.RefreshTokenGrant> findByRawToken(String rawToken) {
			return Optional.empty();
		}

		@Override
		public void revoke(UUID externalId, RevocationReason reason) {
			// Not exercised by UserAdminServiceTest.
		}

		@Override
		public void revokeFamily(UUID familyId, RevocationReason reason) {
			// Not exercised by UserAdminServiceTest.
		}

		@Override
		public void revokeAllForUser(UUID userExternalId, RevocationReason reason) {
			revokedForUser.put(userExternalId, reason);
		}
	}

	private static final class FakeAuditWritePort implements AuditWritePort {

		private final List<AuditEntryCommand> recorded = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			recorded.add(command);
		}
	}

	private static final class FakePasswordHasherPort implements PasswordHasherPort {

		@Override
		public boolean matches(String rawPassword, String hashedPassword) {
			return ("hashed:" + rawPassword).equals(hashedPassword);
		}

		@Override
		public String hash(String rawPassword) {
			return "hashed:" + rawPassword;
		}
	}
}
