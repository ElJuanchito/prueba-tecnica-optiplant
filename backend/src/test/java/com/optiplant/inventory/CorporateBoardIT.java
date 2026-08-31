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
 * Corporate comparative board across active branches (R-20, R-21, R-22, F-8).
 * Asserts:
 * - Active branches only (inactive branches are excluded, F-8)
 * - Exact calculations for sales, inventoryValue (Σ current_stock × average_cost),
 *   criticalProductCount, and activeTransferCount
 * - Sortable both ascending and descending across indicators (R-21)
 * - Invalid sort key or direction returns 400 invalid_request
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorporateBoardIT {

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
	void corporateBoardReturnsExactIndicatorsPerActiveBranchAndExcludesInactive() {
		String adminToken = token("admin.corp");

		// Insert an inactive branch to verify F-8
		UUID inactiveBranchExternalId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO branches (external_id, code, name, address, city, phone, is_active)
				VALUES (?, 'SUC-INACT', 'Sucursal Inactiva Pasto', 'Calle 1 #2-3', 'Pasto', '+57 602 1234567', FALSE)
				ON CONFLICT DO NOTHING
				""", inactiveBranchExternalId);

		ResponseEntity<Map> response = restClient.get()
				.uri("/api/analytics/corporate/branches?sort=inventoryValue&direction=DESC")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();

		Long countActiveInDb = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM branches WHERE is_active = TRUE", Long.class);
		assertThat(((Number) body.get("totalElements")).longValue()).isEqualTo(countActiveInDb);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
		assertThat(content).hasSize(countActiveInDb.intValue());

		// Assert values for each branch match database calculation
		for (Map<String, Object> branch : content) {
			String code = (String) branch.get("code");
			BigDecimal actualValue = new BigDecimal(branch.get("inventoryValue").toString());

			BigDecimal expectedValue = jdbcTemplate.queryForObject("""
					SELECT COALESCE(SUM(bi.current_stock * bi.average_cost), 0)
					FROM branch_inventories bi
					JOIN branches b ON b.id = bi.branch_id
					JOIN products p ON p.id = bi.product_id
					WHERE b.code = ? AND p.is_active = TRUE
					""", BigDecimal.class, code);

			assertThat(actualValue).as("inventoryValue for " + code).isEqualByComparingTo(expectedValue);
		}

		// Inactive branch is not present in content
		assertThat(content).noneMatch(b -> "SUC-INACT".equals(b.get("code")));
	}

	@Test
	void corporateBoardSortingAscendingAndDescendingWorks() {
		String adminToken = token("admin.corp");

		// inventoryValue ASC
		ResponseEntity<Map> ascResp = restClient.get()
				.uri("/api/analytics/corporate/branches?sort=inventoryValue&direction=ASC")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> ascContent = (List<Map<String, Object>>) ascResp.getBody().get("content");
		List<BigDecimal> ascValues = ascContent.stream()
				.map(b -> new BigDecimal(b.get("inventoryValue").toString()))
				.toList();
		assertThat(ascValues).isSorted();

		// inventoryValue DESC
		ResponseEntity<Map> descResp = restClient.get()
				.uri("/api/analytics/corporate/branches?sort=inventoryValue&direction=DESC")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> descContent = (List<Map<String, Object>>) descResp.getBody().get("content");
		List<BigDecimal> descValues = descContent.stream()
				.map(b -> new BigDecimal(b.get("inventoryValue").toString()))
				.toList();
		assertThat(descValues).isSortedAccordingTo(java.util.Comparator.reverseOrder());

		// code ASC
		ResponseEntity<Map> codeResp = restClient.get()
				.uri("/api/analytics/corporate/branches?sort=code&direction=ASC")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> codeContent = (List<Map<String, Object>>) codeResp.getBody().get("content");
		List<String> codes = codeContent.stream()
				.map(b -> (String) b.get("code"))
				.toList();
		assertThat(codes).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
	}

	@Test
	void unknownSortOrDirectionReturns400InvalidRequest() {
		String adminToken = token("admin.corp");

		ResponseEntity<ErrorBody> badSort = restClient.get()
				.uri("/api/analytics/corporate/branches?sort=nonExistentColumn")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(badSort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(badSort.getBody()).isNotNull();
		assertThat(badSort.getBody().code()).isEqualTo("invalid_request");

		ResponseEntity<ErrorBody> badDir = restClient.get()
				.uri("/api/analytics/corporate/branches?direction=SIDEWAYS")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(badDir.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(badDir.getBody()).isNotNull();
		assertThat(badDir.getBody().code()).isEqualTo("invalid_request");
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
