package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Branch isolation for {@code inventory} and {@code notifications} against a real PostgreSQL 17
 * (Testcontainers) — R-01, R-19, R-24 (tasks.md 3.2). Named {@code InventoryBranchIsolationIT},
 * not {@code BranchIsolationIT}: that name already exists (from {@code iam}) in this package and
 * would collide (design §11 trap 1).
 *
 * <p>{@code CrossBranchAccessDeniedException}'s 403 is not exercised here: no endpoint in this
 * module accepts a branch parameter, so that state is unreachable from the current API surface
 * (its own javadoc says so) and is instead covered by {@code BranchScopePolicyTest}'s unit tests.
 * {@code BranchContextRequiredException} on a mutation/read is covered by {@code InventoryRbacIT}
 * (tasks.md 3.6), which owns the RBAC matrix.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryBranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");
	// RIEGO-MANG-16MM, seeded in every branch (tasks.md 3.2 stock isolation sub-test).
	private static final UUID SEED_RIEGO_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000005");
	// FERT-NPK-151515, seeded in every branch (tasks.md 3.2 Kardex isolation sub-test).
	private static final UUID SEED_NPK_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// Seeded system_alerts (02-seed-data.sql §9): Medellín (CRITICAL) and Cali (WARNING).
	private static final UUID SEED_ALERT_MEDELLIN = UUID.fromString("a0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_ALERT_CALI = UUID.fromString("a0000000-0000-0000-0000-000000000002");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// --- R-01: own-branch stock query -----------------------------------

	@Test
	void anOperatorSeesOnlyTheirOwnBranchsStockForTheSameProduct() {
		String bogota = token("operador.bogota");
		String medellin = token("operador.medellin");

		// A distinctive write-off against Bogotá only, so its balance is guaranteed to diverge from
		// whatever Medellín's independent row currently holds — not an assumption on seed literals.
		writeOff(bogota, SEED_RIEGO_PRODUCT, new BigDecimal("1.0000"), "branch isolation probe");

		BigDecimal bogotaStockFromDb = rawStock(BRANCH_BOGOTA, SEED_RIEGO_PRODUCT);
		BigDecimal medellinStockFromDb = rawStock(BRANCH_MEDELLIN, SEED_RIEGO_PRODUCT);

		StockLineResponse fromBogota = ownStockLine(bogota, SEED_RIEGO_PRODUCT);
		StockLineResponse fromMedellin = ownStockLine(medellin, SEED_RIEGO_PRODUCT);

		assertThat(fromBogota.currentStock()).isEqualByComparingTo(bogotaStockFromDb);
		assertThat(fromMedellin.currentStock()).isEqualByComparingTo(medellinStockFromDb);
		assertThat(fromBogota.currentStock()).isNotEqualByComparingTo(fromMedellin.currentStock());
	}

	// --- R-19: Kardex isolation -------------------------------------------

	@Test
	void aBranchManagerCannotSeeAnotherBranchsKardexMovement() {
		String gerenteBogota = token("gerente.bogota");
		String gerenteMedellin = token("gerente.medellin");

		BigDecimal medellinBalance = rawStock(BRANCH_MEDELLIN, SEED_NPK_PRODUCT);
		MovementReceiptResponse receipt = adjust(gerenteMedellin, SEED_NPK_PRODUCT,
				medellinBalance.add(BigDecimal.ONE), "kardex isolation probe");

		List<UUID> bogotaKardex = kardexExternalIds(gerenteBogota, SEED_NPK_PRODUCT);
		List<UUID> medellinKardex = kardexExternalIds(gerenteMedellin, SEED_NPK_PRODUCT);

		assertThat(bogotaKardex).doesNotContain(receipt.movementExternalId());
		assertThat(medellinKardex).contains(receipt.movementExternalId());
	}

	// --- R-24: alert isolation --------------------------------------------

	@Test
	void alertListingIsScopedToTheCallersOwnBranch() {
		String gerenteBogota = token("gerente.bogota");
		String gerenteMedellin = token("gerente.medellin");
		String gerenteCali = token("gerente.cali");

		List<UUID> bogotaAlerts = alertExternalIds(gerenteBogota);
		List<UUID> medellinAlerts = alertExternalIds(gerenteMedellin);
		List<UUID> caliAlerts = alertExternalIds(gerenteCali);

		assertThat(bogotaAlerts).doesNotContain(SEED_ALERT_MEDELLIN, SEED_ALERT_CALI);
		assertThat(medellinAlerts).contains(SEED_ALERT_MEDELLIN).doesNotContain(SEED_ALERT_CALI);
		assertThat(caliAlerts).contains(SEED_ALERT_CALI).doesNotContain(SEED_ALERT_MEDELLIN);
	}

	@Test
	void resolvingAnotherBranchsAlertRespondsAsIfItDidNotExist() {
		String gerenteBogota = token("gerente.bogota");

		ResponseEntity<ErrorBody> response = restClient.method(org.springframework.http.HttpMethod.PATCH)
				.uri("/api/notifications/alerts/{id}/resolve", SEED_ALERT_MEDELLIN)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteBogota)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("alert_not_found");

		// R-24's "as if it did not exist" must not have side effects: the alert is untouched.
		Boolean stillUnresolved = jdbcTemplate.queryForObject(
				"SELECT NOT is_resolved FROM system_alerts WHERE external_id = ?", Boolean.class, SEED_ALERT_MEDELLIN);
		assertThat(stillUnresolved).isTrue();
	}

	// --- helpers -----------------------------------------------------------

	private BigDecimal rawStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
	}

	private StockLineResponse ownStockLine(String token, UUID productExternalId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get()
				.uri("/api/inventory/stock?productExternalId=" + productExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(Map.class);
		assertThat(page).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		assertThat(content).as("expected exactly one stock line for " + productExternalId).hasSize(1);
		Map<String, Object> line = content.get(0);
		return new StockLineResponse(new BigDecimal(line.get("currentStock").toString()));
	}

	private void writeOff(String token, UUID productExternalId, BigDecimal quantity, String reason) {
		restClient.post().uri("/api/inventory/write-offs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", productExternalId, "quantity", quantity, "reason", reason))
				.retrieve().toBodilessEntity();
	}

	private MovementReceiptResponse adjust(String token, UUID productExternalId, BigDecimal countedQuantity,
			String reason) {
		return restClient.post().uri("/api/inventory/adjustments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", productExternalId, "countedQuantity", countedQuantity, "reason",
						reason))
				.retrieve().body(MovementReceiptResponse.class);
	}

	private List<UUID> kardexExternalIds(String token, UUID productExternalId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get()
				.uri("/api/inventory/kardex?productExternalId=" + productExternalId + "&size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(Map.class);
		assertThat(page).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		return content.stream().map(row -> UUID.fromString(row.get("externalId").toString())).toList();
	}

	private List<UUID> alertExternalIds(String token) {
		@SuppressWarnings("unchecked")
		Map<String, Object> page = restClient.get().uri("/api/notifications/alerts?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(Map.class);
		assertThat(page).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		return content.stream().map(row -> UUID.fromString(row.get("externalId").toString())).toList();
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record StockLineResponse(BigDecimal currentStock) {
	}

	private record MovementReceiptResponse(UUID movementExternalId) {
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
