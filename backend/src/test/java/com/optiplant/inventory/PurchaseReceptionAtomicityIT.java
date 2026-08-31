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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Proves R-15, R-18, R-20, and T-01 against a real PostgreSQL 17 (Testcontainers):
 * <ul>
 *   <li>A successful reception increments stock and recalculates average_cost to the exact RN-10 value.</li>
 *   <li>Exactly one {@code PURCHASE_RECEIPT} Kardex row is written per received line with
 *       {@code reference_type = 'PURCHASE_ORDER'} and {@code reference_id} = order external_id.</li>
 *   <li>A forced mid-reception failure rolls back all lines, balances, average_cost, Kardex, and order status.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchaseReceptionAtomicityIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");

	// FERT-NPK-151515, seeded with 5000 KG at Bogotá @ average_cost 3200.0000
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// BIO-FOL-AMINO, seeded with 450 L at Bogotá @ average_cost 48000.0000
	private static final UUID PRODUCT_FOLIAR = UUID.fromString("d0000000-0000-0000-0000-000000000002");

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
	void successfulReceptionAtomicallyIncrementsStockRecalculatesWacAndWritesKardexAndAudit() {
		BigDecimal npkStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal npkAverageBefore = averageCost(BRANCH_BOGOTA, PRODUCT_NPK);

		// Order 5000 units with unit_cost 5000.0000 and 20.00% discount => effective_cost = 4000.0000
		// RN-10 formula: (stockBefore * averageBefore + receivedQuantity * effectiveCost) / (stockBefore + receivedQuantity)
		BigDecimal orderedQuantity = new BigDecimal("5000.0000");
		BigDecimal unitCost = new BigDecimal("5000.0000");
		BigDecimal discountPercent = new BigDecimal("20.00");
		BigDecimal expectedEffectiveCost = new BigDecimal("4000.0000");
		BigDecimal expectedNewAverage = npkStockBefore.multiply(npkAverageBefore)
				.add(orderedQuantity.multiply(expectedEffectiveCost))
				.divide(npkStockBefore.add(orderedQuantity), 4, java.math.RoundingMode.HALF_UP);

		Map<String, Object> order = createOrder(bogotaToken, SUPPLIER_AGROFERTIL, List.of(
				Map.of(
						"productExternalId", PRODUCT_NPK,
						"quantity", orderedQuantity,
						"unitCost", unitCost,
						"discountPercent", discountPercent
				)
		));
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		// Approve order
		approveOrder(bogotaToken, orderId);

		// Receive order
		Map<String, Object> receiveRequest = Map.of(
				"notes", "Recepcion atomica completa",
				"items", List.of(
						Map.of(
								"itemExternalId", itemId,
								"receivedQuantity", orderedQuantity
						)
				)
		);

		@SuppressWarnings("unchecked")
		ResponseEntity<Map> response = restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(receiveRequest)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.get("status")).isEqualTo("RECEIVED");

		// Stock incremented exactly by received quantity
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK))
				.isEqualByComparingTo(npkStockBefore.add(orderedQuantity));

		// Average cost recalculated to exact RN-10 value
		assertThat(averageCost(BRANCH_BOGOTA, PRODUCT_NPK))
				.isEqualByComparingTo(expectedNewAverage);

		// Kardex row created with reference_type = 'PURCHASE_ORDER' and reference_id = order external_id
		List<Map<String, Object>> kardexRows = jdbcTemplate.queryForList(
				"SELECT movement_type, quantity, unit_cost, reference_type, reference_id FROM kardex_movements "
						+ "WHERE reference_type = 'PURCHASE_ORDER' AND reference_id = ?",
				orderId.toString()
		);
		assertThat(kardexRows).hasSize(1);
		Map<String, Object> kardex = kardexRows.get(0);
		assertThat(kardex.get("movement_type")).isEqualTo("PURCHASE_RECEIPT");
		assertThat(kardex.get("reference_type")).isEqualTo("PURCHASE_ORDER");
		assertThat(kardex.get("reference_id")).isEqualTo(orderId.toString());
		assertThat(new BigDecimal(kardex.get("quantity").toString())).isEqualByComparingTo(orderedQuantity);
		assertThat(new BigDecimal(kardex.get("unit_cost").toString())).isEqualByComparingTo(expectedEffectiveCost);

		// Audit log recorded
		Long auditCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = 'RECEIVE_PURCHASE_ORDER' AND entity_id = ?",
				Long.class, orderId.toString()
		);
		assertThat(auditCount).isEqualTo(1L);
	}

	@Test
	void forcedMidReceptionFailureLeavesOrderBalancesAverageCostAndKardexUntouched() {
		BigDecimal npkQuantity = new BigDecimal("10.0000");
		BigDecimal foliarQuantity = new BigDecimal("50.0000");

		BigDecimal npkStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal npkAverageBefore = averageCost(BRANCH_BOGOTA, PRODUCT_NPK);
		BigDecimal foliarStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR);
		BigDecimal foliarAverageBefore = averageCost(BRANCH_BOGOTA, PRODUCT_FOLIAR);

		// Create 2-line order (NPK and FOLIAR)
		Map<String, Object> order = createOrder(bogotaToken, SUPPLIER_AGROFERTIL, List.of(
				Map.of("productExternalId", PRODUCT_NPK, "quantity", npkQuantity, "unitCost", new BigDecimal("3500.0000")),
				Map.of("productExternalId", PRODUCT_FOLIAR, "quantity", foliarQuantity, "unitCost", new BigDecimal("50000.0000"))
		));
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		List<UUID> itemIds = itemExternalIdsOf(order);
		assertThat(itemIds).hasSize(2);

		approveOrder(bogotaToken, orderId);

		long kardexCountBefore = countKardexForOrder(orderId);

		// Force numeric overflow on the second line (FOLIAR) during StockMutationAdapter save:
		// max NUMERIC(14,4) is 9999999999.9999. Setting current_stock near max will cause line 2 applyMovement to overflow.
		jdbcTemplate.update(
				"UPDATE branch_inventories SET current_stock = 9999999990.0000 WHERE branch_id = 1 AND product_id = 2"
		);

		try {
			Map<String, Object> receiveRequest = Map.of(
					"notes", "Recepcion fallida mid-flight",
					"items", List.of(
							Map.of("itemExternalId", itemIds.get(0), "receivedQuantity", npkQuantity),
							Map.of("itemExternalId", itemIds.get(1), "receivedQuantity", foliarQuantity)
					)
			);

			ResponseEntity<String> response = restClient.post()
					.uri("/api/purchases/orders/{id}/receptions", orderId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(receiveRequest)
					.retrieve()
					.onStatus(status -> true, (req, res) -> {})
					.toEntity(String.class);

			assertThat(response.getStatusCode().isError()).isTrue();
		} finally {
			// Restore product foliar stock to original
			jdbcTemplate.update(
					"UPDATE branch_inventories SET current_stock = ? WHERE branch_id = 1 AND product_id = 2",
					foliarStockBefore
			);
		}

		// Balances and average_cost untouched
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(npkStockBefore);
		assertThat(averageCost(BRANCH_BOGOTA, PRODUCT_NPK)).isEqualByComparingTo(npkAverageBefore);
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR)).isEqualByComparingTo(foliarStockBefore);
		assertThat(averageCost(BRANCH_BOGOTA, PRODUCT_FOLIAR)).isEqualByComparingTo(foliarAverageBefore);

		// Kardex untouched
		assertThat(countKardexForOrder(orderId)).isEqualTo(kardexCountBefore);

		// Order status still APPROVED and received_quantity = 0
		Map<String, Object> detail = getOrderDetail(bogotaToken, orderId);
		assertThat(detail.get("status")).isEqualTo("APPROVED");
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
		assertThat(items).allMatch(i -> new BigDecimal(i.get("receivedQuantity").toString()).compareTo(BigDecimal.ZERO) == 0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createOrder(String token, UUID supplierExternalId, List<Map<String, Object>> items) {
		Map<String, Object> request = Map.of(
				"supplierExternalId", supplierExternalId,
				"paymentTerms", "Contado",
				"notes", "Order atomicity fixture",
				"items", items
		);
		Map<String, Object> response = restClient.post()
				.uri("/api/purchases/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(response).isNotNull();
		return response;
	}

	private void approveOrder(String token, UUID orderId) {
		ResponseEntity<Void> response = restClient.post()
				.uri("/api/purchases/orders/{id}/approval", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toBodilessEntity();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getOrderDetail(String token, UUID orderId) {
		Map<String, Object> detail = restClient.get()
				.uri("/api/purchases/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.body(Map.class);
		assertThat(detail).isNotNull();
		return detail;
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	@SuppressWarnings("unchecked")
	private List<UUID> itemExternalIdsOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		return items.stream().map(i -> UUID.fromString((String) i.get("externalId"))).toList();
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

	private long countKardexForOrder(UUID orderExternalId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements WHERE reference_type = 'PURCHASE_ORDER' AND reference_id = ?",
				Long.class, orderExternalId.toString());
		return count == null ? 0L : count;
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
