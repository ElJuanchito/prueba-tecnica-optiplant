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
 * Critical replenishment panel against real PostgreSQL 17 (R-15, R-16, R-17, R-18, F-3).
 * Asserts:
 * - Empty branch answers an informative empty page with 200 (R-18)
 * - Column-to-column predicate current_stock <= min_stock_threshold returns exact rows (F-3)
 * - Default sorting places OUT_OF_STOCK first (R-16)
 * - coverageDays is 0 when current_stock is 0
 * - severity and sort query params work correctly
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReplenishmentPanelIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_MAIZ = UUID.fromString("d0000000-0000-0000-0000-000000000003");
	private static final UUID PRODUCT_FUNG = UUID.fromString("d0000000-0000-0000-0000-000000000004");

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
	void branchWithNothingBelowThresholdReturns200WithEmptyPage() {
		String adminToken = token("admin.corp");

		UUID cleanBranchId = UUID.randomUUID();
		long branchPk = jdbcTemplate.queryForObject("""
				INSERT INTO branches (external_id, code, name, address, city, phone, is_active)
				VALUES (?, ?, 'Sucursal Limpia', 'Calle 100', 'Tunja', '+57 608 1234567', TRUE)
				RETURNING id
				""", Long.class, cleanBranchId, "SUC-CLEAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

		// Insert stock well above threshold
		jdbcTemplate.update("""
				INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost)
				VALUES (?, 1, 1000.0000, 0.0000, 0.0000, 10.0000, 1000.0000)
				""", branchPk);

		ResponseEntity<Map> response = restClient.get()
				.uri("/api/analytics/replenishment?branchExternalId={id}", cleanBranchId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(0L);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
		assertThat(content).isEmpty();
	}

	@Test
	void replenishmentReturnsExactRowsWithSeverityOrderingAndCoverage() {
		UUID branchExternalId = UUID.randomUUID();
		String branchCode = "SUC-REPL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		long branchPk = jdbcTemplate.queryForObject("""
				INSERT INTO branches (external_id, code, name, address, city, phone, is_active)
				VALUES (?, ?, 'Sucursal Reposicion Test', 'Autopista Norte km 12', 'Medellín', '+57 604 4482310', TRUE)
				RETURNING id
				""", Long.class, branchExternalId, branchCode);

		String managerUsername = "gerente.repl." + UUID.randomUUID().toString().substring(0, 8);
		jdbcTemplate.update("""
				INSERT INTO users (external_id, branch_id, username, email, password_hash, full_name, role, is_active)
				VALUES (?, ?, ?, ?, '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Gerente Reposicion', 'BRANCH_MANAGER', TRUE)
				""", UUID.randomUUID(), branchPk, managerUsername, managerUsername + "@optiplant.com");

		// Seed product 4 (FUNG): OUT_OF_STOCK (current_stock = 0 <= 30)
		jdbcTemplate.update("""
				INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost)
				VALUES (?, 4, 0.0000, 0.0000, 0.0000, 30.0000, 66000.0000)
				""", branchPk);

		// Seed product 3 (MAIZ): CRITICAL (current_stock = 12 <= 25)
		jdbcTemplate.update("""
				INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost)
				VALUES (?, 3, 12.0000, 0.0000, 0.0000, 25.0000, 385000.0000)
				""", branchPk);

		// Seed product 1 (NPK): HEALTHY (current_stock = 1000 > 10) - must NOT appear in results
		jdbcTemplate.update("""
				INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost)
				VALUES (?, 1, 1000.0000, 0.0000, 0.0000, 10.0000, 3200.0000)
				""", branchPk);

		String managerToken = token(managerUsername);

		// Default sort: OUT_OF_STOCK first, then CRITICAL
		ResponseEntity<Map> response = restClient.get()
				.uri("/api/analytics/replenishment")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(2L);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
		assertThat(content).hasSize(2);

		// Item 0: OUT_OF_STOCK (FUNG)
		Map<String, Object> item0 = content.get(0);
		assertThat(item0.get("productExternalId")).isEqualTo(PRODUCT_FUNG.toString());
		assertThat(item0.get("sku")).isEqualTo("FUNG-BIO-TRICH");
		assertThat(new BigDecimal(item0.get("currentStock").toString())).isEqualByComparingTo("0.0000");
		assertThat(new BigDecimal(item0.get("minStockThreshold").toString())).isEqualByComparingTo("30.0000");
		assertThat(item0.get("severity")).isEqualTo("OUT_OF_STOCK");
		assertThat(new BigDecimal(item0.get("coverageDays").toString())).isEqualByComparingTo("0.00");

		// Item 1: CRITICAL (MAIZ)
		Map<String, Object> item1 = content.get(1);
		assertThat(item1.get("productExternalId")).isEqualTo(PRODUCT_MAIZ.toString());
		assertThat(item1.get("sku")).isEqualTo("SEM-MAIZ-H300");
		assertThat(new BigDecimal(item1.get("currentStock").toString())).isEqualByComparingTo("12.0000");
		assertThat(new BigDecimal(item1.get("minStockThreshold").toString())).isEqualByComparingTo("25.0000");
		assertThat(item1.get("severity")).isEqualTo("CRITICAL");

		// Filter severity=OUT_OF_STOCK
		ResponseEntity<Map> outOfStockOnly = restClient.get()
				.uri("/api/analytics/replenishment?severity=OUT_OF_STOCK")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> oosContent = (List<Map<String, Object>>) outOfStockOnly.getBody().get("content");
		assertThat(oosContent).hasSize(1);
		assertThat(oosContent.get(0).get("severity")).isEqualTo("OUT_OF_STOCK");

		// Filter severity=CRITICAL
		ResponseEntity<Map> criticalOnly = restClient.get()
				.uri("/api/analytics/replenishment?severity=CRITICAL")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> critContent = (List<Map<String, Object>>) criticalOnly.getBody().get("content");
		assertThat(critContent).hasSize(1);
		assertThat(critContent.get(0).get("severity")).isEqualTo("CRITICAL");

		// Sort by product name
		ResponseEntity<Map> sortProduct = restClient.get()
				.uri("/api/analytics/replenishment?sort=product")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> sortedByName = (List<Map<String, Object>>) sortProduct.getBody().get("content");
		assertThat(sortedByName).hasSize(2);
		assertThat(sortedByName.get(0).get("sku")).isEqualTo("FUNG-BIO-TRICH"); // "Biofungicida..."
		assertThat(sortedByName.get(1).get("sku")).isEqualTo("SEM-MAIZ-H300");  // "Semilla..."
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record LoginRequestBody(String username, String password) {}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds,
			String role, String branchId, String branchName, String branchCode) {}
}
