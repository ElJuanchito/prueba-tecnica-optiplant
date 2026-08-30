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
 * External POS intake verification against a real PostgreSQL 17 (Testcontainers)
 * — R-26, R-27, R-28, R-29, P-07, and D-5 (tasks.md 3.6).
 *
 * <ul>
 *   <li>The POS path produces the same database rows as the internal path.</li>
 *   <li>Ignores/refuses any branch parameter in the payload ({@code 400 invalid_request}).</li>
 *   <li>Refuses a retried receipt number with {@code 409 duplicate_invoice_number}.</li>
 *   <li>Refuses absent or unknown API keys with {@code 401 invalid_api_credential}.</li>
 *   <li>Refuses an invoice number matching the internal {@code VEN-} pattern with {@code 400 invalid_request}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"optiplant.sales.external.api-keys[0].key=pos-api-key-test-secret-value",
		"optiplant.sales.external.api-keys[0].branch-external-id=b0000000-0000-0000-0000-000000000001",
		"optiplant.sales.external.api-keys[0].user-external-id=e0000000-0000-0000-0000-000000000005"
})
class ExternalSaleIntakeIT {

	private static final String API_KEY_HEADER = "X-Api-Key";
	private static final String VALID_API_KEY = "pos-api-key-test-secret-value";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
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
	void validExternalSaleProducesIdenticalRowsToInternalPath() {
		String receiptNumber = "POS-BOG-" + UUID.randomUUID().toString().substring(0, 8);
		BigDecimal quantity = new BigDecimal("4.0000");
		BigDecimal stockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);

		Map<String, Object> request = Map.of(
				"invoiceNumber", receiptNumber,
				"customerName", "Cliente POS Mostrador",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", quantity))
		);

		@SuppressWarnings("unchecked")
		ResponseEntity<Map> response = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.get("invoiceNumber")).isEqualTo(receiptNumber);
		assertThat(body.get("status")).isEqualTo("COMPLETED");

		UUID saleExternalId = UUID.fromString((String) body.get("externalId"));

		// Database checks:
		// 1. sales row
		Map<String, Object> saleRow = jdbcTemplate.queryForMap(
				"SELECT invoice_number, branch_id, status FROM sales WHERE external_id = ?",
				saleExternalId
		);
		assertThat(saleRow.get("invoice_number")).isEqualTo(receiptNumber);
		assertThat(((Number) saleRow.get("branch_id")).intValue()).isEqualTo(1);
		assertThat(saleRow.get("status")).isEqualTo("COMPLETED");

		// 2. kardex row
		List<Map<String, Object>> kardexRows = jdbcTemplate.queryForList(
				"SELECT movement_type, reference_type, quantity FROM kardex_movements "
						+ "WHERE reference_type = 'SALE_INVOICE' AND reference_id = ?",
				saleExternalId.toString()
		);
		assertThat(kardexRows).hasSize(1);
		assertThat(kardexRows.get(0).get("movement_type")).isEqualTo("SALE");

		// 3. stock decrement
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(stockBefore.subtract(quantity));

		// 4. audit log
		Long auditCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = 'REGISTER_SALE' AND entity_id = ?",
				Long.class, saleExternalId.toString()
		);
		assertThat(auditCount).isEqualTo(1L);
	}

	@Test
	void retriedReceiptNumberIsRefusedWithDuplicateInvoiceNumber() {
		String receiptNumber = "POS-RETRY-" + UUID.randomUUID().toString().substring(0, 8);
		BigDecimal quantity = new BigDecimal("2.0000");

		Map<String, Object> request = Map.of(
				"invoiceNumber", receiptNumber,
				"customerName", "Cliente Retry",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", quantity))
		);

		// First submission succeeds
		ResponseEntity<String> first = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(String.class);
		assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		BigDecimal stockAfterFirst = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);

		// Second submission with the exact same invoice number is refused with 409 duplicate_invoice_number
		ResponseEntity<ErrorBody> second = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(second.getBody()).isNotNull();
		assertThat(second.getBody().code()).isEqualTo("duplicate_invoice_number");

		// Stock is NOT decremented a second time
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(stockAfterFirst);
	}

	@Test
	void branchFieldInPayloadIsRefusedWithInvalidRequest() {
		Map<String, Object> request = Map.of(
				"invoiceNumber", "POS-BRANCH-" + UUID.randomUUID().toString().substring(0, 8),
				"branchExternalId", UUID.randomUUID(),
				"customerName", "Cliente Malicioso",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void reservedInternalPatternInvoiceNumberIsRefusedWithInvalidRequest() {
		Map<String, Object> request = Map.of(
				"invoiceNumber", "VEN-2026-9999",
				"customerName", "Cliente Spoof",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, VALID_API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void absentOrInvalidApiKeyReturns401InvalidApiCredential() {
		Map<String, Object> request = Map.of(
				"invoiceNumber", "POS-KEY-" + UUID.randomUUID().toString().substring(0, 8),
				"customerName", "Cliente Sin Auth",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		// 1. Absent key
		ResponseEntity<ErrorBody> noKeyResponse = restClient.post()
				.uri("/api/external/sales")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(noKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(noKeyResponse.getBody()).isNotNull();
		assertThat(noKeyResponse.getBody().code()).isEqualTo("invalid_api_credential");

		// 2. Unknown key
		ResponseEntity<ErrorBody> badKeyResponse = restClient.post()
				.uri("/api/external/sales")
				.header(API_KEY_HEADER, "wrong-api-key-value")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(badKeyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(badKeyResponse.getBody()).isNotNull();
		assertThat(badKeyResponse.getBody().code()).isEqualTo("invalid_api_credential");
	}

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
	}

	private record ErrorBody(String code, String message) {
	}
}
