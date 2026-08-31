package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Verifies R-01, P-01, P-02: analytics is strictly read-only.
 * After exercising all seven endpoints, row counts of sales, sale_items,
 * branch_inventories, kardex_movements, transfers, transfer_items, audit_logs,
 * and system_alerts are completely unchanged.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"optiplant.analytics.external.api-keys[0].key=avail-read-only-test-key",
		"optiplant.analytics.external.api-keys[0].user-external-id=e0000000-0000-0000-0000-000000000005"
})
class AnalyticsReadOnlyIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final String API_KEY = "avail-read-only-test-key";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

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
	void exercisingAllSevenEndpointsMutatesNoDatabaseState() {
		String managerToken = token("gerente.bogota");
		String adminToken = token("admin.corp");

		long salesBefore = count("sales");
		long saleItemsBefore = count("sale_items");
		long inventoriesBefore = count("branch_inventories");
		long kardexBefore = count("kardex_movements");
		long transfersBefore = count("transfers");
		long transferItemsBefore = count("transfer_items");
		long auditLogsBefore = count("audit_logs");
		long alertsBefore = count("system_alerts");

		// 1. Sales trend
		ResponseEntity<String> salesTrend = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?months=4")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(salesTrend.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 2. Rotation
		ResponseEntity<String> rotation = restClient.get()
				.uri("/api/analytics/dashboard/rotation")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(rotation.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 3. Transfers summary
		ResponseEntity<String> transfersSummary = restClient.get()
				.uri("/api/analytics/dashboard/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(transfersSummary.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 4. Transfers stock impact
		ResponseEntity<String> stockImpact = restClient.get()
				.uri("/api/analytics/dashboard/transfers/stock-impact")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(stockImpact.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 5. Replenishment
		ResponseEntity<String> replenishment = restClient.get()
				.uri("/api/analytics/replenishment")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(replenishment.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 6. Corporate board
		ResponseEntity<String> corporate = restClient.get()
				.uri("/api/analytics/corporate/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);
		assertThat(corporate.getStatusCode()).isEqualTo(HttpStatus.OK);

		// 7. External availability
		ResponseEntity<String> externalAvail = restClient.get()
				.uri("/api/external/availability/{productExternalId}", PRODUCT_NPK)
				.header("X-Api-Key", API_KEY)
				.retrieve()
				.toEntity(String.class);
		assertThat(externalAvail.getStatusCode()).isEqualTo(HttpStatus.OK);

		// Assert all table counts are identical (R-01)
		assertThat(count("sales")).as("sales count unchanged").isEqualTo(salesBefore);
		assertThat(count("sale_items")).as("sale_items count unchanged").isEqualTo(saleItemsBefore);
		assertThat(count("branch_inventories")).as("branch_inventories count unchanged").isEqualTo(inventoriesBefore);
		assertThat(count("kardex_movements")).as("kardex_movements count unchanged").isEqualTo(kardexBefore);
		assertThat(count("transfers")).as("transfers count unchanged").isEqualTo(transfersBefore);
		assertThat(count("transfer_items")).as("transfer_items count unchanged").isEqualTo(transferItemsBefore);
		assertThat(count("audit_logs")).as("audit_logs count unchanged").isEqualTo(auditLogsBefore);
		assertThat(count("system_alerts")).as("system_alerts count unchanged").isEqualTo(alertsBefore);
	}

	private long count(String table) {
		Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
		return count != null ? count : 0L;
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
