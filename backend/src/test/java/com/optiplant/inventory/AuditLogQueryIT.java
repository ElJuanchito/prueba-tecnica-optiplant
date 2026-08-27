package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Full audit-query spec against a real PostgreSQL 17 (Testcontainers): the five
 * RF-SEG-04 filters (user, branch, entity, action, date range), pagination, and
 * role-scoping (ADMIN sees every branch, BRANCH_MANAGER is forced to their own,
 * OPERATOR is denied). Audit rows are inserted directly via {@link JdbcTemplate} — no
 * production mutation endpoint exists yet to generate them naturally (user/branch
 * admin arrive in slices 5a/5b); {@code AuditAtomicityIT} is the test that exercises
 * {@code AuditWritePort} itself. Every scenario tags its own rows with a random,
 * per-test {@code entity_name} marker so assertions stay exact regardless of
 * execution order or any other IT class's rows sharing this container.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuditLogQueryIT {

	private static final String SEED_PASSWORD = "Password123!";
	// Seed order is fixed (backend/init-db/02-seed-data.sql): branches 1=Bogotá,
	// 2=Medellín; users 1=admin.corp, 2=gerente.bogota.
	private static final long BOGOTA_BRANCH_ID = 1L;
	private static final long MEDELLIN_BRANCH_ID = 2L;
	private static final long ADMIN_CORP_USER_ID = 1L;
	private static final UUID ADMIN_CORP_EXTERNAL_ID = UUID.fromString("e0000000-0000-0000-0000-000000000001");
	private static final UUID MEDELLIN_BRANCH_EXTERNAL_ID = UUID
			.fromString("b0000000-0000-0000-0000-000000000002");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void adminVeEntradasDeTodasLasSucursales() {
		String marca = marker();
		insertar(ADMIN_CORP_USER_ID, BOGOTA_BRANCH_ID, "CREATE", marca, "u1", Instant.now());
		insertar(ADMIN_CORP_USER_ID, MEDELLIN_BRANCH_ID, "CREATE", marca, "u2", Instant.now());

		AuditPageResponse pagina = consultar(tokenPara("admin.corp"), "entityName", marca);

		assertThat(pagina.totalElements()).isEqualTo(2);
	}

	@Test
	void gerenteDeSucursalSoloVeSuPropiaSucursalAunqueEnvieOtraEnElFiltro() {
		String marca = marker();
		insertar(ADMIN_CORP_USER_ID, BOGOTA_BRANCH_ID, "UPDATE", marca, "u1", Instant.now());
		insertar(ADMIN_CORP_USER_ID, MEDELLIN_BRANCH_ID, "UPDATE", marca, "u2", Instant.now());

		AuditPageResponse pagina = consultar(tokenPara("gerente.bogota"), "entityName", marca, "branchId",
				MEDELLIN_BRANCH_EXTERNAL_ID.toString());

		assertThat(pagina.totalElements()).isEqualTo(1);
		assertThat(pagina.content().get(0).branchId())
				.isEqualTo(UUID.fromString("b0000000-0000-0000-0000-000000000001"));
	}

	@Test
	void operadorEsRechazadoConCuatroCientosTres() {
		ResponseEntity<String> respuesta = restClient.get()
				.uri("/api/audit")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPara("operador.bogota"))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void filtraPorUsuarioActor() {
		String marca = marker();
		insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca, "solo-admin", Instant.now());
		insertar(2L, null, "CREATE", marca, "gerente-bogota-tambien", Instant.now());

		AuditPageResponse pagina = consultar(tokenPara("admin.corp"), "entityName", marca, "userId",
				ADMIN_CORP_EXTERNAL_ID.toString());

		assertThat(pagina.totalElements()).isEqualTo(1);
		assertThat(pagina.content().get(0).entityId()).isEqualTo("solo-admin");
	}

	@Test
	void filtraPorEntidad() {
		String marca = marker();
		insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca + "-branches", "b1", Instant.now());
		insertar(ADMIN_CORP_USER_ID, null, "UPDATE", marca + "-users", "u1", Instant.now());

		AuditPageResponse pagina = consultar(tokenPara("admin.corp"), "entityName", marca + "-branches");

		assertThat(pagina.totalElements()).isEqualTo(1);
		assertThat(pagina.content().get(0).entityId()).isEqualTo("b1");
	}

	@Test
	void filtraPorAccion() {
		String marca = marker();
		insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca, "creado", Instant.now());
		insertar(ADMIN_CORP_USER_ID, null, "DISABLE", marca, "deshabilitado", Instant.now());

		AuditPageResponse pagina = consultar(tokenPara("admin.corp"), "entityName", marca, "action", "DISABLE");

		assertThat(pagina.totalElements()).isEqualTo(1);
		assertThat(pagina.content().get(0).entityId()).isEqualTo("deshabilitado");
	}

	@Test
	void filtraPorRangoDeFechas() {
		String marca = marker();
		Instant hace2Dias = Instant.now().minus(2, ChronoUnit.DAYS);
		Instant hoy = Instant.now();
		insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca, "viejo", hace2Dias);
		insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca, "reciente", hoy);
		String corteUnDiaAtras = Instant.now().minus(1, ChronoUnit.DAYS).toString();

		AuditPageResponse desdeElCorte = consultar(tokenPara("admin.corp"), "entityName", marca, "from",
				corteUnDiaAtras);
		AuditPageResponse hastaElCorte = consultar(tokenPara("admin.corp"), "entityName", marca, "to",
				corteUnDiaAtras);

		assertThat(desdeElCorte.totalElements()).isEqualTo(1);
		assertThat(desdeElCorte.content().get(0).entityId()).isEqualTo("reciente");
		assertThat(hastaElCorte.totalElements()).isEqualTo(1);
		assertThat(hastaElCorte.content().get(0).entityId()).isEqualTo("viejo");
	}

	@Test
	void unConjuntoMasGrandeQueElTamanoDePaginaPorDefectoSePagina() {
		String marca = marker();
		for (int i = 0; i < 25; i++) {
			insertar(ADMIN_CORP_USER_ID, null, "CREATE", marca, "entrada-" + i, Instant.now());
		}

		AuditPageResponse pagina = consultar(tokenPara("admin.corp"), "entityName", marca);

		assertThat(pagina.content()).hasSize(20);
		assertThat(pagina.totalElements()).isEqualTo(25);
	}

	private void insertar(Long userId, Long branchId, String action, String entityName, String entityId,
			Instant createdAt) {
		jdbcTemplate.update(
				"INSERT INTO audit_logs (user_id, branch_id, action, entity_name, entity_id, created_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?)",
				userId, branchId, action, entityName, entityId, Timestamp.from(createdAt));
	}

	/** {@code entity_name} is {@code VARCHAR(50)}; a full UUID marker would overflow
	 * it once a test appends its own suffix (found by executing, not by reading —
	 * CLAUDE.md). 8 hex chars, mirroring {@code AuthenticationFlowIT.shortSuffix()},
	 * is short enough while still unique per test run. */
	private static String marker() {
		return "it-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private AuditPageResponse consultar(String accessToken, String... queryParams) {
		return restClient.get()
				.uri(uriBuilder -> {
					uriBuilder.path("/api/audit");
					for (int i = 0; i < queryParams.length; i += 2) {
						uriBuilder.queryParam(queryParams[i], queryParams[i + 1]);
					}
					return uriBuilder.build();
				})
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(AuditPageResponse.class);
	}

	private String tokenPara(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId) {
	}

	private record AuditEntryResponseBody(UUID externalId, UUID actorUserId, UUID branchId, String action,
			String entityName, String entityId, String payloadBefore, String payloadAfter, String ipAddress,
			Instant createdAt) {
	}

	private record AuditPageResponse(List<AuditEntryResponseBody> content, long totalElements, int page, int size) {
	}
}
