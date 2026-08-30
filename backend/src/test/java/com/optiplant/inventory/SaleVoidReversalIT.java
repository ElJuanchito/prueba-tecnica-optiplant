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
 * Proves R-19, R-20, and R-21 against a real PostgreSQL 17 (Testcontainers):
 * <ul>
 *   <li>The void adds an {@code ADJUSTMENT_POS} movement with {@code reference_type = 'SALE_VOID'}
 *       at the original {@code SALE} movement's unit cost.</li>
 *   <li>The original {@code SALE} row survives unchanged.</li>
 *   <li>Replaying the Kardex from {@code INITIAL_LOAD} reproduces {@code current_stock}.</li>
 *   <li>{@code average_cost} is unmoved.</li>
 *   <li>Subsequent void attempt fails with {@code 409 invalid_sale_state}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleVoidReversalIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = token("gerente.bogota");
	}

	@Test
	void voidReversesStockWithAdjustmentPosAtOriginalUnitCostAndLeavesAverageCostUnmoved() {
		BigDecimal initialStock = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal initialAvgCost = averageCost(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal soldQuantity = new BigDecimal("15.0000");

		// 1. Register sale
		Map<String, Object> saleRequest = Map.of(
				"customerName", "Comprador de Prueba",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", soldQuantity))
		);

		@SuppressWarnings("unchecked")
		Map<String, Object> saleCreated = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(saleRequest)
				.retrieve()
				.body(Map.class);
		assertThat(saleCreated).isNotNull();
		UUID saleExternalId = UUID.fromString((String) saleCreated.get("externalId"));

		// Stock decremented after sale
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(initialStock.subtract(soldQuantity));

		// Find the original SALE Kardex row
		Map<String, Object> saleKardexRow = jdbcTemplate.queryForMap(
				"SELECT id, unit_cost, quantity, movement_type, reference_type FROM kardex_movements "
						+ "WHERE reference_type = 'SALE_INVOICE' AND reference_id = ?",
				saleExternalId.toString()
		);
		BigDecimal originalUnitCost = (BigDecimal) saleKardexRow.get("unit_cost");
		assertThat(originalUnitCost).isNotNull();

		// 2. Void the sale
		String cancellationReason = "Cliente desistio de la compra en mostrador";
		Map<String, Object> voidRequest = Map.of("reason", cancellationReason);

		@SuppressWarnings("unchecked")
		ResponseEntity<Map> voidResponse = restClient.post()
				.uri("/api/sales/{id}/cancellation", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(voidRequest)
				.retrieve()
				.toEntity(Map.class);

		assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> voidBody = voidResponse.getBody();
		assertThat(voidBody).isNotNull();
		assertThat(voidBody.get("status")).isEqualTo("CANCELLED");
		assertThat(voidBody.get("cancellationReason")).isEqualTo(cancellationReason);

		// 3. Invariants verification:
		// (a) Original SALE row survives unchanged
		List<Map<String, Object>> originalSaleRows = jdbcTemplate.queryForList(
				"SELECT movement_type, quantity, unit_cost FROM kardex_movements "
						+ "WHERE reference_type = 'SALE_INVOICE' AND reference_id = ?",
				saleExternalId.toString()
		);
		assertThat(originalSaleRows).hasSize(1);
		assertThat(originalSaleRows.get(0).get("movement_type")).isEqualTo("SALE");

		// (b) New ADJUSTMENT_POS row with reference_type = 'SALE_VOID' at original unit cost
		List<Map<String, Object>> voidRows = jdbcTemplate.queryForList(
				"SELECT movement_type, quantity, unit_cost, reference_type FROM kardex_movements "
						+ "WHERE reference_type = 'SALE_VOID' AND reference_id = ?",
				saleExternalId.toString()
		);
		assertThat(voidRows).hasSize(1);
		assertThat(voidRows.get(0).get("movement_type")).isEqualTo("ADJUSTMENT_POS");
		assertThat((BigDecimal) voidRows.get(0).get("unit_cost")).isEqualByComparingTo(originalUnitCost);
		assertThat((BigDecimal) voidRows.get(0).get("quantity")).isEqualByComparingTo(soldQuantity);

		// (c) Stock restored to initial balance
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(initialStock);

		// (d) average_cost is unmoved
		assertThat(averageCost(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(initialAvgCost);

		// (e) Kardex replay from INITIAL_LOAD reproduces current_stock
		List<Map<String, Object>> allMovements = jdbcTemplate.queryForList(
				"SELECT movement_type, previous_stock, resulting_stock FROM kardex_movements k "
						+ "JOIN branches b ON b.id = k.branch_id JOIN products p ON p.id = k.product_id "
						+ "WHERE b.external_id = ? AND p.external_id = ? ORDER BY k.created_at ASC, k.id ASC",
				BRANCH_BOGOTA, PRODUCT_NPK
		);
		assertThat(allMovements).isNotEmpty();
		assertThat(allMovements.get(0).get("movement_type")).isEqualTo("INITIAL_LOAD");

		for (int i = 0; i < allMovements.size() - 1; i++) {
			BigDecimal resulting = (BigDecimal) allMovements.get(i).get("resulting_stock");
			BigDecimal nextPrevious = (BigDecimal) allMovements.get(i + 1).get("previous_stock");
			assertThat(nextPrevious).as("row %d resulting_stock must equal row %d previous_stock", i, i + 1)
					.isEqualByComparingTo(resulting);
		}
		BigDecimal lastResulting = (BigDecimal) allMovements.get(allMovements.size() - 1).get("resulting_stock");
		assertThat(lastResulting).isEqualByComparingTo(currentStock(BRANCH_BOGOTA, PRODUCT_NPK));

		// (f) Audit log recorded for VOID_SALE
		Long auditCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = 'VOID_SALE' AND entity_id = ?",
				Long.class, saleExternalId.toString()
		);
		assertThat(auditCount).isEqualTo(1L);

		// 4. Subsequent void attempt refused with 409 invalid_sale_state
		ResponseEntity<ErrorBody> doubleVoid = restClient.post()
				.uri("/api/sales/{id}/cancellation", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", "Intento de segunda anulacion"))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(doubleVoid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(doubleVoid.getBody()).isNotNull();
		assertThat(doubleVoid.getBody().code()).isEqualTo("invalid_sale_state");
	}

	@Test
	void voidWithBlankReasonIsRefused() {
		// Register sale
		Map<String, Object> saleRequest = Map.of(
				"customerName", "Cliente Para Anulacion",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		@SuppressWarnings("unchecked")
		Map<String, Object> saleCreated = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(saleRequest)
				.retrieve()
				.body(Map.class);
		assertThat(saleCreated).isNotNull();
		UUID saleExternalId = UUID.fromString((String) saleCreated.get("externalId"));

		ResponseEntity<ErrorBody> blankReasonResponse = restClient.post()
				.uri("/api/sales/{id}/cancellation", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", "   "))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(blankReasonResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(blankReasonResponse.getBody()).isNotNull();
		assertThat(blankReasonResponse.getBody().code()).isIn("sale_reason_required", "invalid_request");
	}

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
	}

	private BigDecimal averageCost(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT average_cost FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
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
