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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * External availability API endpoint via API key (CU-EXT-01, RF-EXT-01, R-23..R-26).
 * Asserts:
 * - Valid API key returns network-wide availability without isOwnBranch (R-24)
 * - Response exposes external_id only, no numeric id and no cost/price fields (R-26, RNF-API-02)
 * - Zero-stock product returns explicit zeroes, never 404 (R-24)
 * - Unknown product returns 404 product_not_found (R-24)
 * - Absent, malformed, or invalid API key returns 401 invalid_api_credential (R-25)
 * - /api/external/sales still authenticates with its own key (trap 5)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"optiplant.analytics.external.api-keys[0].key=avail-secret-api-key-test",
		"optiplant.analytics.external.api-keys[0].user-external-id=e0000000-0000-0000-0000-000000000005",
		"optiplant.sales.external.api-keys[0].key=sales-pos-key-test-value",
		"optiplant.sales.external.api-keys[0].branch-external-id=b0000000-0000-0000-0000-000000000001",
		"optiplant.sales.external.api-keys[0].user-external-id=e0000000-0000-0000-0000-000000000005"
})
class ExternalAvailabilityIT {

	private static final String API_KEY_HEADER = "X-Api-Key";
	private static final String VALID_AVAIL_KEY = "avail-secret-api-key-test";
	private static final String VALID_SALES_KEY = "sales-pos-key-test-value";
	private static final UUID PRODUCT_MAIZ = UUID.fromString("d0000000-0000-0000-0000-000000000003");

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
	void validApiKeyReturnsConsolidatedNetworkAvailability() {
		// MAIZ (product 3):
		// Bogotá: 120 current, 0 reserved, 0 in_transit -> 120 available
		// Medellín: 12 current, 0 reserved, 0 in_transit -> 12 available
		// Cali: 5 current, 0 reserved, 30 in_transit -> 5 available
		// Network total = 137.0000
		ResponseEntity<String> rawResponse = restClient.get()
				.uri("/api/external/availability/{productExternalId}", PRODUCT_MAIZ)
				.header(API_KEY_HEADER, VALID_AVAIL_KEY)
				.retrieve()
				.toEntity(String.class);

		assertThat(rawResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String bodyStr = rawResponse.getBody();
		assertThat(bodyStr).isNotNull();

		// Negative checks for prohibited fields (R-24, R-26, RNF-API-02)
		assertThat(bodyStr).doesNotContain("isOwnBranch");
		assertThat(bodyStr).doesNotContain("average_cost");
		assertThat(bodyStr).doesNotContain("averageCost");
		assertThat(bodyStr).doesNotContain("unit_price");
		assertThat(bodyStr).doesNotContain("unitPrice");
		assertThat(bodyStr).doesNotContain("\"id\":");

		// Typed verification
		ResponseEntity<Map> response = restClient.get()
				.uri("/api/external/availability/{productExternalId}", PRODUCT_MAIZ)
				.header(API_KEY_HEADER, VALID_AVAIL_KEY)
				.retrieve()
				.toEntity(Map.class);

		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.get("productExternalId")).isEqualTo(PRODUCT_MAIZ.toString());
		assertThat(body.get("sku")).isEqualTo("SEM-MAIZ-H300");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> branches = (List<Map<String, Object>>) body.get("branches");
		assertThat(branches).hasSize(3);

		BigDecimal sumAvailable = branches.stream()
				.map(b -> new BigDecimal(b.get("availableStock").toString()))
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertThat(new BigDecimal(body.get("networkTotal").toString())).isEqualByComparingTo(sumAvailable);
	}

	@Test
	void zeroStockProductReturnsExplicitZeroedResult() {
		// Insert a product with 0 stock everywhere
		UUID zeroStockProduct = UUID.randomUUID();
		long prodPk = jdbcTemplate.queryForObject("""
				INSERT INTO products (external_id, category_id, sku, name, description, base_unit, is_active)
				VALUES (?, 1, 'ZERO-STOCK-PROD', 'Producto Agotado', 'Test', 'KG', TRUE)
				RETURNING id
				""", Long.class, zeroStockProduct);

		for (int b = 1; b <= 3; b++) {
			jdbcTemplate.update("""
					INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost)
					VALUES (?, ?, 0.0000, 0.0000, 0.0000, 10.0000, 1000.0000)
					""", b, prodPk);
		}

		ResponseEntity<Map> response = restClient.get()
				.uri("/api/external/availability/{productExternalId}", zeroStockProduct)
				.header(API_KEY_HEADER, VALID_AVAIL_KEY)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(new BigDecimal(body.get("networkTotal").toString())).isEqualByComparingTo("0.0000");
	}

	@Test
	void unknownProductReturns404ProductNotFound() {
		UUID unknown = UUID.randomUUID();

		ResponseEntity<ErrorBody> response = restClient.get()
				.uri("/api/external/availability/{productExternalId}", unknown)
				.header(API_KEY_HEADER, VALID_AVAIL_KEY)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("product_not_found");
	}

	@Test
	void absentOrInvalidApiKeyReturns401InvalidApiCredential() {
		// Absent key
		ResponseEntity<ErrorBody> absentResp = restClient.get()
				.uri("/api/external/availability/{productExternalId}", PRODUCT_MAIZ)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(absentResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(absentResp.getBody()).isNotNull();
		assertThat(absentResp.getBody().code()).isEqualTo("invalid_api_credential");

		// Invalid key
		ResponseEntity<ErrorBody> invalidResp = restClient.get()
				.uri("/api/external/availability/{productExternalId}", PRODUCT_MAIZ)
				.header(API_KEY_HEADER, "wrong-and-invalid-key")
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(invalidResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(invalidResp.getBody()).isNotNull();
		assertThat(invalidResp.getBody().code()).isEqualTo("invalid_api_credential");
	}

	@Test
	void externalSalesFilterIsNotShadowedByAvailabilityFilter() {
		// Regression check (trap 5): /api/external/sales still works with its own key
		String receiptNumber = "POS-EXT-" + UUID.randomUUID().toString().substring(0, 8);
		Map<String, Object> saleReq = Map.of(
				"invoiceNumber", receiptNumber,
				"customerName", "Cliente POS Test",
				"items", List.of(Map.of("productExternalId", PRODUCT_MAIZ, "quantity", new BigDecimal("1.0000")))
		);

		ResponseEntity<Map> salesResp = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_SALES_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(saleReq)
				.retrieve()
				.toEntity(Map.class);

		assertThat(salesResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private record ErrorBody(String code, String message) {}
}
