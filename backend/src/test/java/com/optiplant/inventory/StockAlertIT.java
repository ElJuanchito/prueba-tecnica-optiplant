package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence.AlertFailureFixtureConfiguration;
import com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence.AlertFailureToggle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * R-20, R-21, R-22, R-24 and P-10 against a real PostgreSQL 17 (Testcontainers): the
 * {@code STOCK_MINIMUM} alert exists after commit, is not duplicated across repeated breaching
 * commits, is never auto-resolved when stock recovers, and a forced failure inside
 * {@code OperationalAlertListener} does not roll back the movement/threshold write that triggered
 * it (tasks.md 3.4).
 *
 * <p>Imports {@link AlertFailureFixtureConfiguration} in addition to the usual
 * {@link TestcontainersConfiguration}, so this class gets its <strong>own</strong> cached Spring
 * context and its own fresh Testcontainers PostgreSQL — pristine seed data, unaffected by any
 * other {@code *IT} class and vice versa.
 */
@Import({ TestcontainersConfiguration.class, AlertFailureFixtureConfiguration.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockAlertIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	// SEM-MAIZ-H300 — used for the no-failure alert lifecycle (dedup, no auto-resolve).
	private static final UUID SEED_SEMILLA_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000003");
	// FUNG-BIO-TRICH — dedicated to the forced-listener-failure scenario.
	private static final UUID SEED_FUNGICIDA_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000004");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AlertFailureToggle alertFailureToggle;

	private RestClient restClient;
	private String gerenteBogota;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		gerenteBogota = token("gerente.bogota");
	}

	@AfterEach
	void tearDown() {
		alertFailureToggle.disable();
	}

	@Test
	void thresholdBreachRaisesAnUndupedAlertAfterCommitAndNeverAutoResolves() {
		BigDecimal current = rawStock(SEED_SEMILLA_PRODUCT);
		BigDecimal breachingThreshold = current.add(BigDecimal.ONE);

		setThreshold(SEED_SEMILLA_PRODUCT, breachingThreshold);

		List<Map<String, Object>> alertsAfterFirstBreach = unresolvedAlerts(SEED_SEMILLA_PRODUCT);
		assertThat(alertsAfterFirstBreach).as("R-20: the alert must exist right after commit").hasSize(1);
		assertThat(alertsAfterFirstBreach.get(0).get("severity")).isEqualTo("WARNING");

		// R-21: a second breaching commit for the same still-open condition must not duplicate it.
		setThreshold(SEED_SEMILLA_PRODUCT, breachingThreshold.add(BigDecimal.ONE));
		assertThat(unresolvedAlerts(SEED_SEMILLA_PRODUCT)).as("R-21: no second row for the same dedup key").hasSize(1);

		// R-22: raising stock back above the threshold must not auto-resolve the alert.
		BigDecimal wellAbove = breachingThreshold.add(BigDecimal.ONE).add(BigDecimal.TEN);
		adjust(SEED_SEMILLA_PRODUCT, wellAbove, "recover stock above threshold");
		assertThat(unresolvedAlerts(SEED_SEMILLA_PRODUCT))
				.as("R-22/PA-03: resolution is an explicit human act, never automatic").hasSize(1);
	}

	@Test
	void aForcedListenerFailureDoesNotRollBackTheTriggeringThresholdWrite() {
		BigDecimal current = rawStock(SEED_FUNGICIDA_PRODUCT);
		BigDecimal breachingThreshold = current.add(BigDecimal.ONE);

		alertFailureToggle.enable();

		ResponseEntity<Map> response = restClient.put()
				.uri("/api/inventory/stock/{id}/threshold", SEED_FUNGICIDA_PRODUCT)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteBogota).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("minStockThreshold", breachingThreshold)).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toEntity(Map.class);

		assertThat(response.getStatusCode()).as("P-10: the listener's own failure must never surface to the caller")
				.isEqualTo(HttpStatus.OK);

		BigDecimal persistedThreshold = jdbcTemplate.queryForObject(
				"SELECT min_stock_threshold FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, BRANCH_BOGOTA, SEED_FUNGICIDA_PRODUCT);
		assertThat(persistedThreshold).as("the threshold write itself must commit despite the listener failing")
				.isEqualByComparingTo(breachingThreshold);

		assertThat(unresolvedAlerts(SEED_FUNGICIDA_PRODUCT))
				.as("the forced failure happened inside AlertRepositoryPort.create, so no alert row exists")
				.isEmpty();
	}

	// --- helpers -------------------------------------------------------

	private List<Map<String, Object>> unresolvedAlerts(UUID productExternalId) {
		return jdbcTemplate.queryForList(
				"SELECT severity FROM system_alerts WHERE branch_id = 1 AND alert_type = 'STOCK_MINIMUM' "
						+ "AND title = ? AND is_resolved = false",
				"STOCK_MINIMUM:" + productExternalId);
	}

	private BigDecimal rawStock(UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, BRANCH_BOGOTA, productExternalId);
	}

	private void setThreshold(UUID productExternalId, BigDecimal threshold) {
		restClient.put().uri("/api/inventory/stock/{id}/threshold", productExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteBogota).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("minStockThreshold", threshold)).retrieve().toBodilessEntity();
	}

	private void adjust(UUID productExternalId, BigDecimal countedQuantity, String reason) {
		restClient.post().uri("/api/inventory/adjustments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + gerenteBogota).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", productExternalId, "countedQuantity", countedQuantity, "reason",
						reason))
				.retrieve().toBodilessEntity();
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
