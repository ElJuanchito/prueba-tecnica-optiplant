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
 * Proves R-03, R-04, R-08, and T-01 against a real PostgreSQL 17 (Testcontainers):
 * <ul>
 *   <li>A successful sale registration atomically decrements stock and creates exactly one {@code SALE}
 *       Kardex row per item with {@code reference_type = 'SALE_INVOICE'}.</li>
 *   <li>A forced mid-sale failure (e.g. overdraw on second item) leaves the sale, balances, and Kardex untouched.</li>
 *   <li>A sale crossing the minimum threshold raises one {@code STOCK_MINIMUM} alert per product {@code AFTER_COMMIT} (P-08).</li>
 *   <li>Insufficient stock writes nothing and returns {@code 409 insufficient_stock}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleRegistrationAtomicityIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");

	// FERT-NPK-151515, seeded with 5000 KG at Bogotá (threshold 500) and 1800 KG at Cali (threshold 300)
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// BIO-FOL-AMINO, seeded with 450 L at Bogotá (threshold 50)
	private static final UUID PRODUCT_FOLIAR = UUID.fromString("d0000000-0000-0000-0000-000000000002");
	// SEM-MAIZ-H300, seeded with 120 Bolsas at Bogotá (threshold 20)
	private static final UUID PRODUCT_MAIZ = UUID.fromString("d0000000-0000-0000-0000-000000000003");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String bogotaToken;
	private String caliToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = token("gerente.bogota");
		caliToken = token("gerente.cali");
	}

	@Test
	void successfulSaleRegistrationAtomicallyDecrementsStockAndWritesKardexRowPerItem() {
		BigDecimal npkQuantity = new BigDecimal("10.0000");
		BigDecimal foliarQuantity = new BigDecimal("5.0000");

		BigDecimal npkStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal foliarStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR);

		Map<String, Object> request = Map.of(
				"customerName", "Agricultura S.A.S.",
				"customerTaxId", "901.234.567-8",
				"taxPercent", new BigDecimal("19.00"),
				"notes", "Venta mostrador atomica",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", npkQuantity),
						Map.of("productExternalId", PRODUCT_FOLIAR, "quantity", foliarQuantity)
				)
		);

		@SuppressWarnings("unchecked")
		ResponseEntity<Map> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		UUID saleExternalId = UUID.fromString((String) body.get("externalId"));
		String invoiceNumber = (String) body.get("invoiceNumber");
		assertThat(invoiceNumber).matches("VEN-\\d{4}-\\d{4,}");

		// Stock decremented
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(npkStockBefore.subtract(npkQuantity));
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR)).isEqualByComparingTo(foliarStockBefore.subtract(foliarQuantity));

		// Exactly one SALE Kardex row per item with reference_type = 'SALE_INVOICE'
		List<Map<String, Object>> kardexRows = jdbcTemplate.queryForList(
				"SELECT product_id, movement_type, quantity, reference_type, reference_id FROM kardex_movements "
						+ "WHERE reference_type = 'SALE_INVOICE' AND reference_id = ?",
				saleExternalId.toString()
		);
		assertThat(kardexRows).hasSize(2);
		assertThat(kardexRows).allMatch(row -> "SALE".equals(row.get("movement_type")));
		assertThat(kardexRows).allMatch(row -> "SALE_INVOICE".equals(row.get("reference_type")));

		// Audit log recorded
		Long auditCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = 'REGISTER_SALE' AND entity_id = ?",
				Long.class, saleExternalId.toString()
		);
		assertThat(auditCount).isEqualTo(1L);
	}

	@Test
	void forcedMidSaleFailureLeavesSaleBalancesAndKardexUntouched() {
		BigDecimal goodQuantity = new BigDecimal("10.0000");
		BigDecimal impossibleQuantity = new BigDecimal("500.0000");

		BigDecimal npkStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal maizStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_MAIZ);
		long salesCountBefore = countSalesForBranch(BRANCH_BOGOTA);
		long kardexCountBefore = countKardexForBranch(BRANCH_BOGOTA);

		Map<String, Object> request = Map.of(
				"customerName", "Cliente Fallido",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", goodQuantity),
						Map.of("productExternalId", PRODUCT_MAIZ, "quantity", impossibleQuantity)
				)
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("insufficient_stock");

		// Balances untouched
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(npkStockBefore);
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_MAIZ)).isEqualByComparingTo(maizStockBefore);

		// Sales and Kardex untouched
		assertThat(countSalesForBranch(BRANCH_BOGOTA)).isEqualTo(salesCountBefore);
		assertThat(countKardexForBranch(BRANCH_BOGOTA)).isEqualTo(kardexCountBefore);
	}

	@Test
	void saleCrossingThresholdRaisesOneStockMinimumAlertPerProduct() {
		// In Cali, PRODUCT_NPK has current_stock 1800 and min_stock_threshold 300.
		// A sale of 1600 brings stock to 200 <= 300, triggering STOCK_MINIMUM alert.
		BigDecimal stockBefore = currentStock(BRANCH_CALI, PRODUCT_NPK);
		BigDecimal saleQuantity = stockBefore.subtract(new BigDecimal("200.0000")); // leaves 200

		Map<String, Object> request = Map.of(
				"customerName", "AgroCali Ltda",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", saleQuantity)
				)
		);

		ResponseEntity<String> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(currentStock(BRANCH_CALI, PRODUCT_NPK)).isEqualByComparingTo(new BigDecimal("200.0000"));

		// Alert raised in system_alerts
		List<Map<String, Object>> alerts = jdbcTemplate.queryForList(
				"SELECT severity, is_resolved FROM system_alerts sa "
						+ "JOIN branches b ON b.id = sa.branch_id "
						+ "WHERE b.external_id = ? AND sa.alert_type = 'STOCK_MINIMUM' AND sa.title = ? AND sa.is_resolved = false",
				BRANCH_CALI, "STOCK_MINIMUM:" + PRODUCT_NPK
		);
		assertThat(alerts).hasSize(1);
		assertThat(alerts.get(0).get("severity")).isEqualTo("WARNING");
	}

	@Test
	void insufficientStockWritesNothing() {
		BigDecimal impossibleQuantity = new BigDecimal("99999.0000");
		long salesCountBefore = countSalesForBranch(BRANCH_BOGOTA);
		long kardexCountBefore = countKardexForBranch(BRANCH_BOGOTA);

		Map<String, Object> request = Map.of(
				"customerName", "Cliente Sin Stock",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", impossibleQuantity)
				)
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("insufficient_stock");

		assertThat(countSalesForBranch(BRANCH_BOGOTA)).isEqualTo(salesCountBefore);
		assertThat(countKardexForBranch(BRANCH_BOGOTA)).isEqualTo(kardexCountBefore);
	}

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
	}

	private long countSalesForBranch(UUID branchExternalId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM sales s JOIN branches b ON b.id = s.branch_id WHERE b.external_id = ?",
				Long.class, branchExternalId);
		return count == null ? 0L : count;
	}

	private long countKardexForBranch(UUID branchExternalId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements k JOIN branches b ON b.id = k.branch_id WHERE b.external_id = ?",
				Long.class, branchExternalId);
		return count == null ? 0L : count;
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
