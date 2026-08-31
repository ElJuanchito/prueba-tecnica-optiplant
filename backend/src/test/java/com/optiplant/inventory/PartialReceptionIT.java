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
 * Proves R-19 and HU-COM-04 against a real PostgreSQL 17 (Testcontainers):
 * <ul>
 *   <li>An order for 100 units partially received (60 units) moves to {@code PARTIALLY_RECEIVED},
 *       accumulates {@code received_quantity = 60}, pending balance 40, and stock increases by 60.</li>
 *   <li>Receiving the remaining 40 units moves to {@code RECEIVED}, {@code received_quantity = 100},
 *       pending balance 0, and stock increases by another 40.</li>
 *   <li>A third reception attempt against a {@code RECEIVED} order is rejected with
 *       {@code 409 invalid_order_state}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartialReceptionIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");
	// FERT-NPK-151515, seeded in Cali with 1800 KG
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String caliToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		caliToken = token("gerente.cali");
	}

	@Test
	void partialReceptionProgressionToCompletionAndSubsequentRefusal() {
		BigDecimal initialStock = currentStock(BRANCH_CALI, PRODUCT_NPK);
		BigDecimal totalOrdered = new BigDecimal("100.0000");
		BigDecimal firstPart = new BigDecimal("60.0000");
		BigDecimal secondPart = new BigDecimal("40.0000");

		// 1. Create order for 100 units
		Map<String, Object> order = createOrder(caliToken, SUPPLIER_AGROFERTIL, List.of(
				Map.of(
						"productExternalId", PRODUCT_NPK,
						"quantity", totalOrdered,
						"unitCost", new BigDecimal("3500.0000"),
						"discountPercent", BigDecimal.ZERO
				)
		));
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		// 2. Approve order
		approveOrder(caliToken, orderId);

		// 3. First reception: 60 units -> PARTIALLY_RECEIVED
		ResponseEntity<Map> firstResponse = receive(caliToken, orderId, itemId, firstPart);
		assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> firstBody = firstResponse.getBody();
		assertThat(firstBody).isNotNull();
		assertThat(firstBody.get("status")).isEqualTo("PARTIALLY_RECEIVED");

		Map<String, Object> itemAfterFirst = firstItem(firstBody);
		assertThat(new BigDecimal(itemAfterFirst.get("orderedQuantity").toString())).isEqualByComparingTo(totalOrdered);
		assertThat(new BigDecimal(itemAfterFirst.get("receivedQuantity").toString())).isEqualByComparingTo(firstPart);
		assertThat(new BigDecimal(itemAfterFirst.get("pendingQuantity").toString())).isEqualByComparingTo(new BigDecimal("40.0000"));

		// Stock incremented by 60
		assertThat(currentStock(BRANCH_CALI, PRODUCT_NPK))
				.isEqualByComparingTo(initialStock.add(firstPart));

		// 4. Second reception: remaining 40 units -> RECEIVED
		ResponseEntity<Map> secondResponse = receive(caliToken, orderId, itemId, secondPart);
		assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> secondBody = secondResponse.getBody();
		assertThat(secondBody).isNotNull();
		assertThat(secondBody.get("status")).isEqualTo("RECEIVED");

		Map<String, Object> itemAfterSecond = firstItem(secondBody);
		assertThat(new BigDecimal(itemAfterSecond.get("orderedQuantity").toString())).isEqualByComparingTo(totalOrdered);
		assertThat(new BigDecimal(itemAfterSecond.get("receivedQuantity").toString())).isEqualByComparingTo(totalOrdered);
		assertThat(new BigDecimal(itemAfterSecond.get("pendingQuantity").toString())).isEqualByComparingTo(BigDecimal.ZERO);

		// Stock incremented by another 40 (total +100)
		assertThat(currentStock(BRANCH_CALI, PRODUCT_NPK))
				.isEqualByComparingTo(initialStock.add(totalOrdered));

		// 5. Third reception attempt -> 409 invalid_order_state
		ResponseEntity<ErrorBody> thirdResponse = restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of(
						"items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", new BigDecimal("1.0000")))
				))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(thirdResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(thirdResponse.getBody()).isNotNull();
		assertThat(thirdResponse.getBody().code()).isEqualTo("invalid_order_state");

		// Stock remains untouched after refused third attempt
		assertThat(currentStock(BRANCH_CALI, PRODUCT_NPK))
				.isEqualByComparingTo(initialStock.add(totalOrdered));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createOrder(String token, UUID supplierExternalId, List<Map<String, Object>> items) {
		Map<String, Object> request = Map.of(
				"supplierExternalId", supplierExternalId,
				"paymentTerms", "30 días",
				"notes", "Partial reception fixture",
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
	private ResponseEntity<Map> receive(String token, UUID orderId, UUID itemId, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", quantity))
		);
		return restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toEntity(Map.class);
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> firstItem(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return items.get(0);
	}

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
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
