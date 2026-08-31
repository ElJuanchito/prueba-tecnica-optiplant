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
 * State machine and authorization tests for purchase orders against a real PostgreSQL 17 (Testcontainers)
 * — R-11, R-12, R-14, RN-15, PA-08, and RNF-INT-02 (tasks.md 3.3).
 *
 * <ul>
 *   <li>Reception refused from {@code PENDING}, from {@code RECEIVED}, and from {@code CANCELLED}
 *       (each &rarr; {@code 409 invalid_order_state}).</li>
 *   <li>An {@code OPERATOR} is refused the approval ({@code 403}).</li>
 *   <li>Cancellation from {@code PARTIALLY_RECEIVED} succeeds, keeps already-received stock,
 *       and writes NO reversal Kardex row and deletes none.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchaseOrderStateMachineIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");
	// BIO-FOL-AMINO, seeded in Bogotá with 450 L
	private static final UUID PRODUCT_FOLIAR = UUID.fromString("d0000000-0000-0000-0000-000000000002");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String bogotaManagerToken;
	private String bogotaOperatorToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaManagerToken = token("gerente.bogota");
		bogotaOperatorToken = token("operador.bogota");
	}

	@Test
	void receptionRefusedFromPendingState() {
		BigDecimal quantity = new BigDecimal("10.0000");
		BigDecimal stockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR);

		Map<String, Object> order = createOrder(bogotaManagerToken, quantity);
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		// Attempt reception while still PENDING
		ResponseEntity<ErrorBody> response = attemptReception(bogotaManagerToken, orderId, itemId, quantity);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_order_state");

		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR)).isEqualByComparingTo(stockBefore);
		assertThat(statusOf(bogotaManagerToken, orderId)).isEqualTo("PENDING");
	}

	@Test
	void receptionRefusedFromReceivedState() {
		BigDecimal quantity = new BigDecimal("10.0000");
		Map<String, Object> order = createOrder(bogotaManagerToken, quantity);
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		approveOrder(bogotaManagerToken, orderId);
		receiveOk(bogotaManagerToken, orderId, itemId, quantity);
		assertThat(statusOf(bogotaManagerToken, orderId)).isEqualTo("RECEIVED");

		// Attempt reception on already RECEIVED order
		ResponseEntity<ErrorBody> response = attemptReception(bogotaManagerToken, orderId, itemId, quantity);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_order_state");
	}

	@Test
	void receptionRefusedFromCancelledState() {
		BigDecimal quantity = new BigDecimal("10.0000");
		Map<String, Object> order = createOrder(bogotaManagerToken, quantity);
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		cancelOrder(bogotaManagerToken, orderId, "Cancelación para prueba de máquina de estados");
		assertThat(statusOf(bogotaManagerToken, orderId)).isEqualTo("CANCELLED");

		// Attempt reception on CANCELLED order
		ResponseEntity<ErrorBody> response = attemptReception(bogotaManagerToken, orderId, itemId, quantity);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_order_state");
	}

	@Test
	void operatorIsRefusedApproval() {
		BigDecimal quantity = new BigDecimal("10.0000");
		Map<String, Object> order = createOrder(bogotaOperatorToken, quantity);
		UUID orderId = UUID.fromString((String) order.get("externalId"));

		// Operator attempts to approve
		ResponseEntity<Void> response = restClient.post()
				.uri("/api/purchases/orders/{id}/approval", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaOperatorToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(statusOf(bogotaManagerToken, orderId)).isEqualTo("PENDING");
	}

	@Test
	void cancellationFromPartiallyReceivedSucceedsKeepsReceivedStockAndWritesNoReversalKardex() {
		BigDecimal initialStock = currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR);
		BigDecimal totalOrdered = new BigDecimal("100.0000");
		BigDecimal receivedPart = new BigDecimal("35.0000");

		Map<String, Object> order = createOrder(bogotaManagerToken, totalOrdered);
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		approveOrder(bogotaManagerToken, orderId);
		receiveOk(bogotaManagerToken, orderId, itemId, receivedPart);

		assertThat(statusOf(bogotaManagerToken, orderId)).isEqualTo("PARTIALLY_RECEIVED");
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR))
				.isEqualByComparingTo(initialStock.add(receivedPart));

		long kardexCountBefore = countKardexForOrder(orderId);
		assertThat(kardexCountBefore).isEqualTo(1L);

		// Cancel order from PARTIALLY_RECEIVED
		String reason = "Proveedor sin disponibilidad restante";
		ResponseEntity<String> cancelResponse = restClient.post()
				.uri("/api/purchases/orders/{id}/cancellation", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaManagerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", reason))
				.retrieve()
				.toEntity(String.class);

		assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = cancelResponse.getBody();
		assertThat(body).isNotNull();
		assertThat(body).contains("\"status\":\"CANCELLED\"");
		assertThat(body).contains("\"cancellationReason\":\"" + reason + "\"");
		assertThat(body).doesNotContain("CANCEL_REASON:");

		// Stock remains incremented by receivedPart (never reversed)
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_FOLIAR))
				.isEqualByComparingTo(initialStock.add(receivedPart));

		// Kardex append-only: no reversal row written, no row deleted (count stays exactly 1)
		assertThat(countKardexForOrder(orderId)).isEqualTo(1L);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createOrder(String token, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"supplierExternalId", SUPPLIER_AGROFERTIL,
				"paymentTerms", "Contado",
				"notes", "State machine fixture",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_FOLIAR, "quantity", quantity, "unitCost", new BigDecimal("45000.0000"))
				)
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

	private void cancelOrder(String token, UUID orderId, String reason) {
		ResponseEntity<Void> response = restClient.post()
				.uri("/api/purchases/orders/{id}/cancellation", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", reason))
				.retrieve()
				.toBodilessEntity();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private void receiveOk(String token, UUID orderId, UUID itemId, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", quantity))
		);
		ResponseEntity<Void> response = restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toBodilessEntity();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private ResponseEntity<ErrorBody> attemptReception(String token, UUID orderId, UUID itemId, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", quantity))
		);
		return restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
	}

	@SuppressWarnings("unchecked")
	private String statusOf(String token, UUID orderId) {
		Map<String, Object> detail = restClient.get()
				.uri("/api/purchases/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.body(Map.class);
		assertThat(detail).isNotNull();
		return (String) detail.get("status");
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
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

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
