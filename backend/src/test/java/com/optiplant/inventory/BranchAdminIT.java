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
 * Full branch-administration spec against a real PostgreSQL 17 (Testcontainers):
 * create with unique code (409 on duplicate), edit updating profile without
 * changing external_id, logical disable that blocks assigned users' login without
 * physical row deletion, query without exposing numeric id, and audit logging.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BranchAdminIT {

	private static final String SEED_PASSWORD = "Password123!";

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = tokenPara("admin.corp", SEED_PASSWORD);
	}

	@Test
	void crearSucursalConExito() {
		String code = "SUC-" + shortSuffix();
		BranchResponseBody creada = crearSucursal(code, "Sucursal " + code, "Av. Principal 123", "Bogotá", "+57 601 5551234");

		assertThat(creada.externalId()).isNotNull();
		assertThat(creada.code()).isEqualTo(code);
		assertThat(creada.name()).isEqualTo("Sucursal " + code);
		assertThat(creada.address()).isEqualTo("Av. Principal 123");
		assertThat(creada.city()).isEqualTo("Bogotá");
		assertThat(creada.phone()).isEqualTo("+57 601 5551234");
		assertThat(creada.active()).isTrue();
	}

	@Test
	void crearConCodigoDuplicadoDevuelve409() {
		String code = "SUC-" + shortSuffix();
		crearSucursal(code, "Primera Sucursal", "Calle 1", "Medellín", "12345");

		ResponseEntity<String> segunda = crearSucursalRaw(code, "Segunda Sucursal", "Calle 2", "Cali", "67890");
		assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void editarActualizaNombreYDireccionPeroMantieneExternalIdYCodigo() {
		String code = "SUC-" + shortSuffix();
		BranchResponseBody creada = crearSucursal(code, "Sucursal Original", "Dirección Original", "Bogotá", "111");

		EditBranchRequestBody edicion = new EditBranchRequestBody("Sucursal Modificada", "Dirección Modificada", "Medellín", "222");
		ResponseEntity<BranchResponseBody> respuesta = restClient.put()
				.uri("/api/admin/branches/{externalId}", creada.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(edicion)
				.retrieve()
				.toEntity(BranchResponseBody.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isNotNull();
		assertThat(respuesta.getBody().externalId()).isEqualTo(creada.externalId()); // external_id immutable
		assertThat(respuesta.getBody().code()).isEqualTo(code); // code unchanged
		assertThat(respuesta.getBody().name()).isEqualTo("Sucursal Modificada");
		assertThat(respuesta.getBody().address()).isEqualTo("Dirección Modificada");
		assertThat(respuesta.getBody().city()).isEqualTo("Medellín");
		assertThat(respuesta.getBody().phone()).isEqualTo("222");
	}

	@Test
	void editarUnExternalIdInexistenteDevuelve404() {
		EditBranchRequestBody edicion = new EditBranchRequestBody("No Existe", "Dir", "Ciudad", "123");
		ResponseEntity<Void> respuesta = restClient.put()
				.uri("/api/admin/branches/{externalId}", UUID.randomUUID())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(edicion)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity();

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deshabilitarUnaSucursalBloqueaElLoginDeSusUsuariosYNoLaBorraFisicamente() {
		String code = "SUC-" + shortSuffix();
		BranchResponseBody sucursal = crearSucursal(code, "Sucursal Deshabilitable", "Dir", "Cali", "333");

		String username = "it.op.branch." + shortSuffix();
		crearUsuario(username, "OPERATOR", sucursal.externalId());

		// 1. User can login initially
		ResponseEntity<LoginResponseBody> loginInicial = login(username, SEED_PASSWORD);
		assertThat(loginInicial.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 2. Disable branch
		ResponseEntity<Void> respuestaDisable = disable(sucursal.externalId());
		assertThat(respuestaDisable.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// 3. Verify branch row still exists with is_active = false
		Integer countSucursal = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM branches WHERE external_id = ?", Integer.class, sucursal.externalId());
		assertThat(countSucursal).isEqualTo(1);

		Boolean activo = jdbcTemplate.queryForObject(
				"SELECT is_active FROM branches WHERE external_id = ?", Boolean.class, sucursal.externalId());
		assertThat(activo).isFalse();

		// 4. User of disabled branch is now blocked from login
		ResponseEntity<String> loginTrasDeshabilitar = loginRaw(username, SEED_PASSWORD);
		assertThat(loginTrasDeshabilitar.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void deshabilitarUnExternalIdInexistenteDevuelve404() {
		ResponseEntity<Void> respuesta = disable(UUID.randomUUID());
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void unGerenteDeSucursalNoAdminEsRechazadoConCuatroCientosTres() {
		String tokenGerente = tokenPara("gerente.bogota", SEED_PASSWORD);

		ResponseEntity<String> respuesta = restClient.get()
				.uri("/api/admin/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGerente)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(String.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void consultarSucursalesNoExponeElIdNumerico() {
		String code = "SUC-" + shortSuffix();
		BranchResponseBody creada = crearSucursal(code, "Sucursal Query", "Dir", "Bogotá", "123");
		disable(creada.externalId());

		Map<String, Object> pagina = restClient.get()
				.uri("/api/admin/branches?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.body(Map.class);

		assertThat(pagina).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) pagina.get("content");
		assertThat(content).isNotEmpty();
		content.forEach(entry -> assertThat(entry).doesNotContainKey("id"));
	}

	@Test
	void mutacionesRegistranAuditoriaConPayloads() {
		String code = "SUC-" + shortSuffix();
		BranchResponseBody creada = crearSucursal(code, "Sucursal Audit", "Dir Audit", "Bogotá", "555");

		// 1. CREATE audit entry
		Map<String, Object> createAudit = jdbcTemplate.queryForMap(
				"SELECT branch_id, payload_before, payload_after FROM audit_logs WHERE entity_name = 'branches' AND entity_id = ? AND action = 'CREATE'",
				creada.externalId().toString());
		assertThat(createAudit.get("payload_before")).isNull();
		assertThat(createAudit.get("payload_after")).isNotNull();
		assertThat(createAudit.get("branch_id")).isNotNull();
		String createPayloadAfter = createAudit.get("payload_after").toString();
		assertThat(createPayloadAfter).contains(code).contains("Sucursal Audit").contains("true");

		// 2. UPDATE audit entry
		EditBranchRequestBody edicion = new EditBranchRequestBody("Sucursal Audit Editada", "Nueva Dir", "Medellín", "777");
		restClient.put()
				.uri("/api/admin/branches/{externalId}", creada.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(edicion)
				.retrieve()
				.toBodilessEntity();

		Map<String, Object> updateAudit = jdbcTemplate.queryForMap(
				"SELECT payload_before, payload_after FROM audit_logs WHERE entity_name = 'branches' AND entity_id = ? AND action = 'UPDATE'",
				creada.externalId().toString());
		assertThat(updateAudit.get("payload_before")).isNotNull();
		assertThat(updateAudit.get("payload_after")).isNotNull();
		assertThat(updateAudit.get("payload_before").toString()).contains("Sucursal Audit");
		assertThat(updateAudit.get("payload_after").toString()).contains("Sucursal Audit Editada");

		// 3. DISABLE audit entry
		disable(creada.externalId());
		Map<String, Object> disableAudit = jdbcTemplate.queryForMap(
				"SELECT payload_before, payload_after FROM audit_logs WHERE entity_name = 'branches' AND entity_id = ? AND action = 'DISABLE'",
				creada.externalId().toString());
		assertThat(disableAudit.get("payload_before")).isNotNull();
		assertThat(disableAudit.get("payload_after")).isNotNull();
		assertThat(disableAudit.get("payload_before").toString()).contains("true");
		assertThat(disableAudit.get("payload_after").toString()).contains("false");
	}

	private BranchResponseBody crearSucursal(String code, String name, String address, String city, String phone) {
		ResponseEntity<BranchResponseBody> respuesta = restClient.post()
				.uri("/api/admin/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateBranchRequestBody(code, name, address, city, phone))
				.retrieve()
				.toEntity(BranchResponseBody.class);
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isNotNull();
		return respuesta.getBody();
	}

	private ResponseEntity<String> crearSucursalRaw(String code, String name, String address, String city, String phone) {
		return restClient.post()
				.uri("/api/admin/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateBranchRequestBody(code, name, address, city, phone))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(String.class);
	}

	private ResponseEntity<Void> disable(UUID externalId) {
		return restClient.method(HttpMethod.PATCH)
				.uri("/api/admin/branches/{externalId}/disable", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity();
	}

	private void crearUsuario(String username, String role, UUID branchId) {
		restClient.post()
				.uri("/api/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateUserRequestBody(username, username + "@optiplant.com", SEED_PASSWORD, "Usuario Test", role, branchId))
				.retrieve()
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
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(String.class);
	}

	private String tokenPara(String username, String password) {
		LoginResponseBody body = login(username, password).getBody();
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private static String shortSuffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record CreateBranchRequestBody(String code, String name, String address, String city, String phone) {
	}

	private record EditBranchRequestBody(String name, String address, String city, String phone) {
	}

	private record BranchResponseBody(UUID externalId, String code, String name, String address, String city,
			String phone, boolean active) {
	}

	private record CreateUserRequestBody(String username, String email, String password, String fullName,
			String role, UUID branchId) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			UUID branchId, String branchName, String branchCode) {
	}
}
