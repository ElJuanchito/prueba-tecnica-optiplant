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
 * Cross-cutting API smoke tests: envelope structure, input validation (R-00, R-07, R-11),
 * no numeric id exposure (RNF-API-02), and no valuation leak outside corporate board (contract §7).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalyticsApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";

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
	void paginationRejectsOutOfRangeSize() {
		String managerToken = token("gerente.bogota");
		String adminToken = token("admin.corp");

		// Rotation: size = 101 -> 400
		ResponseEntity<ErrorBody> rotBadSize = restClient.get()
				.uri("/api/analytics/dashboard/rotation?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(rotBadSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(rotBadSize.getBody()).isNotNull();
		assertThat(rotBadSize.getBody().code()).isEqualTo("invalid_request");

		// Stock impact: size = 0 -> 400
		ResponseEntity<ErrorBody> impactBadSize = restClient.get()
				.uri("/api/analytics/dashboard/transfers/stock-impact?size=0")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(impactBadSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(impactBadSize.getBody()).isNotNull();
		assertThat(impactBadSize.getBody().code()).isEqualTo("invalid_request");

		// Replenishment: size = 101 -> 400
		ResponseEntity<ErrorBody> replBadSize = restClient.get()
				.uri("/api/analytics/replenishment?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(replBadSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(replBadSize.getBody()).isNotNull();
		assertThat(replBadSize.getBody().code()).isEqualTo("invalid_request");

		// Corporate board: size = 101 -> 400
		ResponseEntity<ErrorBody> corpBadSize = restClient.get()
				.uri("/api/analytics/corporate/branches?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(corpBadSize.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(corpBadSize.getBody()).isNotNull();
		assertThat(corpBadSize.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void monthsOutsideOneToTwelveIsRejected() {
		String managerToken = token("gerente.bogota");

		// months = 0
		ResponseEntity<ErrorBody> resp0 = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?months=0")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(resp0.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp0.getBody()).isNotNull();
		assertThat(resp0.getBody().code()).isEqualTo("invalid_request");

		// months = 13
		ResponseEntity<ErrorBody> resp13 = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?months=13")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(resp13.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(resp13.getBody()).isNotNull();
		assertThat(resp13.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void invertedOrOverWideDateRangeIsRejected() {
		String managerToken = token("gerente.bogota");

		// Inverted range: from > to
		ResponseEntity<ErrorBody> inverted = restClient.get()
				.uri("/api/analytics/dashboard/rotation?from=2026-10-01T00:00:00Z&to=2026-09-01T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(inverted.getBody()).isNotNull();
		assertThat(inverted.getBody().code()).isEqualTo("invalid_request");

		// Range > 366 days
		ResponseEntity<ErrorBody> overWide = restClient.get()
				.uri("/api/analytics/dashboard/rotation?from=2024-01-01T00:00:00Z&to=2026-01-01T00:00:00Z")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(overWide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(overWide.getBody()).isNotNull();
		assertThat(overWide.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void noNumericIdOrUnauthorizedValuationExposed() {
		String managerToken = token("gerente.bogota");

		// Check non-corporate responses for valuation leak or numeric id
		String[] endpoints = {
				"/api/analytics/dashboard/sales-trend",
				"/api/analytics/dashboard/rotation",
				"/api/analytics/dashboard/transfers",
				"/api/analytics/dashboard/transfers/stock-impact",
				"/api/analytics/replenishment"
		};

		for (String ep : endpoints) {
			ResponseEntity<String> response = restClient.get()
					.uri(ep)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
					.retrieve()
					.toEntity(String.class);

			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
			String body = response.getBody();
			assertThat(body).isNotNull();

			// No database numeric id field
			assertThat(body).as("No numeric id in " + ep).doesNotContain("\"id\":");

			// No inventoryValue on non-corporate endpoints
			assertThat(body).as("No inventoryValue in " + ep).doesNotContain("inventoryValue");
			assertThat(body).as("No inventory_value in " + ep).doesNotContain("inventory_value");
			assertThat(body).as("No average_cost in " + ep).doesNotContain("average_cost");
			assertThat(body).as("No averageCost in " + ep).doesNotContain("averageCost");
		}
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

	private record ErrorBody(String code, String message) {}

	private record LoginRequestBody(String username, String password) {}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds,
			String role, String branchId, String branchName, String branchCode) {}
}
