package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Full user-administration spec against a real PostgreSQL 17 (Testcontainers):
 * disabling a user revokes every one of their live refresh tokens (P2/P4),
 * the user row is never physically deleted, and duplicate/validation
 * rejections surface with the right HTTP status. "Historical movements
 * remain intact" (user-administration's disable scenario) is not
 * independently verifiable yet — no movement-producing module exists in this
 * codebase (deferred to a future epic); "no physical delete of the user row"
 * is the closest verifiable proxy this slice can assert directly.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserAdminIT {

	private static final String SEED_PASSWORD = "Password123!";
	// Seed order is fixed (backend/init-db/02-seed-data.sql).
	private static final UUID BOGOTA_BRANCH_EXTERNAL_ID = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID MEDELLIN_BRANCH_EXTERNAL_ID = UUID.fromString("b0000000-0000-0000-0000-000000000002");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;
	private String gerenteBogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = tokenPara("admin.corp", SEED_PASSWORD);
		gerenteBogotaToken = tokenPara("gerente.bogota", SEED_PASSWORD);
	}

	@Test
	void deshabilitarUnUsuarioRevocaTodosSusTokensVivosYNoLoBorraFisicamente() {
		String username = "it.operador." + shortSuffix();
		UserResponseBody creado = crearUsuario(username, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		// Two independent sessions ("two devices"), each its own refresh-token
		// family — disable must revoke both, not just the presented one.
		LoginResponseBody sesion1 = login(username, SEED_PASSWORD).getBody();
		LoginResponseBody sesion2 = login(username, SEED_PASSWORD).getBody();
		assertThat(sesion1).isNotNull();
		assertThat(sesion2).isNotNull();

		ResponseEntity<Void> respuestaDisable = disable(creado.externalId());
		assertThat(respuestaDisable.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		Integer tokensVivos = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
						+ "WHERE u.external_id = ? AND rt.revoked_at IS NULL",
				Integer.class, creado.externalId());
		assertThat(tokensVivos).isZero();

		Integer tokensRevocadosPorDisable = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id "
						+ "WHERE u.external_id = ? AND rt.revoked_reason = 'USER_DISABLED'",
				Integer.class, creado.externalId());
		assertThat(tokensRevocadosPorDisable).isEqualTo(2);

		Integer filasDeUsuario = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM users WHERE external_id = ?", Integer.class, creado.externalId());
		assertThat(filasDeUsuario).isEqualTo(1); // sigue existiendo — no hay borrado físico

		Boolean activo = jdbcTemplate.queryForObject("SELECT is_active FROM users WHERE external_id = ?",
				Boolean.class, creado.externalId());
		assertThat(activo).isFalse();

		ResponseEntity<String> loginTrasDeshabilitar = loginRaw(username, SEED_PASSWORD);
		assertThat(loginTrasDeshabilitar.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void deshabilitarUnExternalIdInexistenteDevuelve404() {
		ResponseEntity<Void> respuesta = disable(UUID.randomUUID());
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void crearConUsernameDuplicadoDevuelve409() {
		String username = "it.duplicado." + shortSuffix();
		crearUsuario(username, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		ResponseEntity<String> segundaCreacion = crearUsuarioRaw(username, "otro." + shortSuffix() + "@optiplant.com",
				Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		assertThat(segundaCreacion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void crearConEmailDuplicadoDevuelve409() {
		String email = "it.email." + shortSuffix() + "@optiplant.com";
		crearUsuarioConEmail("it.primero." + shortSuffix(), email);

		ResponseEntity<String> segundaCreacion = crearUsuarioRaw("it.segundo." + shortSuffix(), email, Role.OPERATOR,
				BOGOTA_BRANCH_EXTERNAL_ID);

		assertThat(segundaCreacion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void crearUnGerenteDeSucursalSinSucursalDevuelve400() {
		ResponseEntity<String> respuesta = crearUsuarioRaw("it.sinsucursal." + shortSuffix(),
				"it.sinsucursal." + shortSuffix() + "@optiplant.com", Role.BRANCH_MANAGER, null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void editarActualizaElRolYLaSucursalDeUnUsuarioExistente() {
		String username = "it.editar." + shortSuffix();
		UserResponseBody creado = crearUsuario(username, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		EditUserRequestBody edicion = new EditUserRequestBody(creado.email(), creado.fullName(), "BRANCH_MANAGER",
				BOGOTA_BRANCH_EXTERNAL_ID);
		ResponseEntity<UserResponseBody> respuesta = restClient.put()
				.uri("/api/admin/users/{externalId}", creado.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(edicion)
				.retrieve()
				.toEntity(UserResponseBody.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isNotNull();
		assertThat(respuesta.getBody().role()).isEqualTo("BRANCH_MANAGER");
		assertThat(respuesta.getBody().externalId()).isEqualTo(creado.externalId()); // external_id immutable
	}

	@Test
	void unOperadorEsRechazadoConCuatroCientosTresAlConsultarUsuarios() {
		String tokenOperador = tokenPara("operador.bogota", SEED_PASSWORD);

		ResponseEntity<String> respuesta = restClient.get()
				.uri("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOperador)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void unGerenteDeSucursalCreaUnOperadorEnSuPropiaSucursal() {
		UserResponseBody creado = crearUsuarioComo(gerenteBogotaToken, "it.gerente.crea." + shortSuffix(),
				"it.gerente.crea." + shortSuffix() + "@optiplant.com", Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		assertThat(creado.role()).isEqualTo("OPERATOR");
		assertThat(creado.branchId()).isEqualTo(BOGOTA_BRANCH_EXTERNAL_ID);
		assertThat(creado.branchName()).isEqualTo("Sucursal Central Bogotá");
		assertThat(creado.branchCode()).isEqualTo("SUC-BOG");
	}

	@Test
	void unGerenteDeSucursalNoPuedeCrearUnOperadorEnOtraSucursal() {
		ResponseEntity<String> respuesta = crearUsuarioRawComo(gerenteBogotaToken,
				"it.gerente.otra." + shortSuffix(), "it.gerente.otra." + shortSuffix() + "@optiplant.com",
				Role.OPERATOR, MEDELLIN_BRANCH_EXTERNAL_ID);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void unGerenteDeSucursalNoPuedeCrearOtroGerenteDeSucursal() {
		ResponseEntity<String> respuesta = crearUsuarioRawComo(gerenteBogotaToken,
				"it.gerente.gerente." + shortSuffix(), "it.gerente.gerente." + shortSuffix() + "@optiplant.com",
				Role.BRANCH_MANAGER, BOGOTA_BRANCH_EXTERNAL_ID);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void unGerenteDeSucursalNoPuedeGestionarUnOperadorDeOtraSucursal() {
		UserResponseBody operadorMedellin = crearUsuario("it.medellin." + shortSuffix(), Role.OPERATOR,
				MEDELLIN_BRANCH_EXTERNAL_ID);

		ResponseEntity<Void> respuestaDisable = disableComo(gerenteBogotaToken, operadorMedellin.externalId());
		assertThat(respuestaDisable.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void laConsultaDeUnGerenteDeSucursalQuedaAcotadaASuPropiaSucursalYARolOperador() {
		crearUsuario("it.bogota." + shortSuffix(), Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);
		crearUsuario("it.medellin." + shortSuffix(), Role.OPERATOR, MEDELLIN_BRANCH_EXTERNAL_ID);

		Map<String, Object> pagina = restClient.get()
				.uri("/api/admin/users?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteBogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(pagina).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) pagina.get("content");
		assertThat(content).isNotEmpty();
		content.forEach(entry -> {
			assertThat(entry.get("role")).isEqualTo("OPERATOR");
			assertThat(entry.get("branchId")).isEqualTo(BOGOTA_BRANCH_EXTERNAL_ID.toString());
		});
	}

	@Test
	void consultarUsuariosNoExponeElIdNumerico() {
		crearUsuario("it.query." + shortSuffix(), Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		Map<String, Object> pagina = restClient.get()
				.uri("/api/admin/users?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.body(Map.class);

		assertThat(pagina).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) pagina.get("content");
		assertThat(content).isNotEmpty();
		content.forEach(entry -> assertThat(entry).doesNotContainKey("id").doesNotContainKey("passwordHash"));
	}

	@Test
	void mutacionesRegistranAuditoriaConPayloads() {
		String username = "it.audit." + shortSuffix();
		String email = username + "@optiplant.com";
		UserResponseBody creado = crearUsuarioConEmail(username, email, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		// 1. CREATE audit entry
		Map<String, Object> createAudit = jdbcTemplate.queryForMap(
				"SELECT payload_before, payload_after FROM audit_logs WHERE entity_name = 'users' AND entity_id = ? AND action = 'CREATE'",
				creado.externalId().toString());
		assertThat(createAudit.get("payload_before")).isNull();
		assertThat(createAudit.get("payload_after")).isNotNull();
		String createPayloadAfter = createAudit.get("payload_after").toString();
		assertThat(createPayloadAfter).contains("OPERATOR").contains(email).contains("true");

		// 2. UPDATE audit entry
		EditUserRequestBody edicion = new EditUserRequestBody(email, "Nombre Editado", "BRANCH_MANAGER",
				BOGOTA_BRANCH_EXTERNAL_ID);
		restClient.put()
				.uri("/api/admin/users/{externalId}", creado.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(edicion)
				.retrieve()
				.toBodilessEntity();

		Map<String, Object> updateAudit = jdbcTemplate.queryForMap(
				"SELECT payload_before, payload_after FROM audit_logs WHERE entity_name = 'users' AND entity_id = ? AND action = 'UPDATE'",
				creado.externalId().toString());
		assertThat(updateAudit.get("payload_before")).isNotNull();
		assertThat(updateAudit.get("payload_after")).isNotNull();
		assertThat(updateAudit.get("payload_before").toString()).contains("OPERATOR");
		assertThat(updateAudit.get("payload_after").toString()).contains("BRANCH_MANAGER");

		// 3. DISABLE audit entry
		disable(creado.externalId());
		Map<String, Object> disableAudit = jdbcTemplate.queryForMap(
				"SELECT payload_before, payload_after FROM audit_logs WHERE entity_name = 'users' AND entity_id = ? AND action = 'DISABLE'",
				creado.externalId().toString());
		assertThat(disableAudit.get("payload_before")).isNotNull();
		assertThat(disableAudit.get("payload_after")).isNotNull();
		assertThat(disableAudit.get("payload_before").toString()).contains("true");
		assertThat(disableAudit.get("payload_after").toString()).contains("false");
	}

	@Test
	void violacionDeConstraintUnicoEnBaseDeDatosDevuelve409() {
		String username = "it.race." + shortSuffix();
		crearUsuario(username, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);

		ResponseEntity<String> intento = crearUsuarioRaw(username, "otro." + shortSuffix() + "@optiplant.com",
				Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);
		assertThat(intento.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(intento.getBody()).contains("duplicate_user_field");
	}

	private UserResponseBody crearUsuario(String username, Role role, UUID branchId) {
		return crearUsuarioConEmail(username, username + "@optiplant.com", role, branchId);
	}

	private UserResponseBody crearUsuarioConEmail(String username, String email) {
		return crearUsuarioConEmail(username, email, Role.OPERATOR, BOGOTA_BRANCH_EXTERNAL_ID);
	}

	private UserResponseBody crearUsuarioConEmail(String username, String email, Role role, UUID branchId) {
		return crearUsuarioComo(adminToken, username, email, role, branchId);
	}

	private UserResponseBody crearUsuarioComo(String token, String username, String email, Role role,
			UUID branchId) {
		ResponseEntity<UserResponseBody> respuesta = restClient.post()
				.uri("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateUserRequestBody(username, email, SEED_PASSWORD, "Usuario de Prueba", role.name(),
						branchId))
				.retrieve()
				.toEntity(UserResponseBody.class);
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isNotNull();
		return respuesta.getBody();
	}

	private ResponseEntity<String> crearUsuarioRaw(String username, String email, Role role, UUID branchId) {
		return crearUsuarioRawComo(adminToken, username, email, role, branchId);
	}

	private ResponseEntity<String> crearUsuarioRawComo(String token, String username, String email, Role role,
			UUID branchId) {
		return restClient.post()
				.uri("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateUserRequestBody(username, email, SEED_PASSWORD, "Usuario de Prueba", role.name(),
						branchId))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
	}

	private ResponseEntity<Void> disable(UUID externalId) {
		return disableComo(adminToken, externalId);
	}

	private ResponseEntity<Void> disableComo(String token, UUID externalId) {
		return restClient.method(HttpMethod.PATCH)
				.uri("/api/admin/users/{externalId}/disable", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toBodilessEntity();
	}

	private ResponseEntity<LoginResponseBody> login(String username, String password) {
		return restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password))
				.retrieve()
				.toEntity(LoginResponseBody.class);
	}

	private ResponseEntity<String> loginRaw(String username, String password) {
		return restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, password))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
	}

	private String tokenPara(String username, String password) {
		LoginResponseBody body = login(username, password).getBody();
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	/** {@code users.username} is {@code VARCHAR(50)}; a full UUID would overflow it. */
	private static String shortSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private enum Role {
		ADMIN, BRANCH_MANAGER, OPERATOR
	}

	private record CreateUserRequestBody(String username, String email, String password, String fullName,
			String role, UUID branchId) {
	}

	private record EditUserRequestBody(String email, String fullName, String role, UUID branchId) {
	}

	private record UserResponseBody(UUID externalId, String username, String email, String fullName, String role,
			UUID branchId, String branchName, String branchCode, boolean active) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}
}
