package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Concurrency tests for purchase orders against a real PostgreSQL 17 (Testcontainers)
 * — R-21, T-02, and F-9 (tasks.md 3.4).
 *
 * <ul>
 *   <li>Two concurrent receptions against the same purchase order serialize under pessimistic locking:
 *       no line is double-counted, neither request answers 500, exactly one wins the full reception and
 *       the other is rejected.</li>
 *   <li>Two simultaneous purchase order creations yield two distinct {@code order_number} values under
 *       the advisory lock (F-9).</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchaseConcurrencyIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");

	// FUNG-BIO-TRICH in Cali (seeded with 80 units)
	private static final UUID PRODUCT_TRICHODERMA = UUID.fromString("d0000000-0000-0000-0000-000000000004");
	// FERT-NPK-151515 in Bogotá
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String caliToken;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		caliToken = token("gerente.cali");
		bogotaToken = token("gerente.bogota");
	}

	@Test
	void twoConcurrentReceptionsOnSameOrderSerializeAndDoNotDoubleCount() throws Exception {
		BigDecimal targetQuantity = new BigDecimal("50.0000");
		BigDecimal stockBefore = currentStock(BRANCH_CALI, PRODUCT_TRICHODERMA);

		// Create and approve order of 50 units
		Map<String, Object> order = createOrder(caliToken, PRODUCT_TRICHODERMA, targetQuantity);
		UUID orderId = UUID.fromString((String) order.get("externalId"));
		UUID itemId = itemExternalIdOf(order);

		approveOrder(caliToken, orderId);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<ResponseEntity<ErrorBody>>> tasks = List.of(
				() -> raceReceive(caliToken, orderId, itemId, targetQuantity, ready, start),
				() -> raceReceive(caliToken, orderId, itemId, targetQuantity, ready, start)
		);

		List<Future<ResponseEntity<ErrorBody>>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<ResponseEntity<ErrorBody>> results = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<HttpStatusCode> statuses = results.stream().map(ResponseEntity::getStatusCode).toList();
		assertThat(statuses).as("no concurrent reception may ever surface a 500")
				.noneMatch(HttpStatusCode::is5xxServerError);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.OK)).count())
				.as("exactly one concurrent full reception must win").isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CONFLICT)).count())
				.as("the losing concurrent reception must be rejected with 409 invalid_order_state").isEqualTo(1);

		ResponseEntity<ErrorBody> loser = results.stream()
				.filter(r -> r.getStatusCode().equals(HttpStatus.CONFLICT))
				.findFirst()
				.orElseThrow();
		assertThat(loser.getBody()).isNotNull();
		assertThat(loser.getBody().code()).isEqualTo("invalid_order_state");

		// Stock incremented by exactly 50 (never 100)
		assertThat(currentStock(BRANCH_CALI, PRODUCT_TRICHODERMA))
				.isEqualByComparingTo(stockBefore.add(targetQuantity));

		// Only 1 Kardex row written
		long kardexCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements WHERE reference_type = 'PURCHASE_ORDER' AND reference_id = ?",
				Long.class, orderId.toString()
		);
		assertThat(kardexCount).isEqualTo(1L);
	}

	@Test
	void twoSimultaneousOrderCreationsProduceDistinctOrderNumbers() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<String>> tasks = List.of(
				() -> raceCreateOrderAndGetNumber(bogotaToken, ready, start),
				() -> raceCreateOrderAndGetNumber(bogotaToken, ready, start)
		);

		List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<String> orderNumbers = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(orderNumbers).hasSize(2);
		assertThat(orderNumbers.get(0)).matches("OC-\\d{4}-\\d{4,}");
		assertThat(orderNumbers.get(1)).matches("OC-\\d{4}-\\d{4,}");
		assertThat(orderNumbers.get(0)).isNotEqualTo(orderNumbers.get(1));
	}

	private ResponseEntity<ErrorBody> raceReceive(String token, UUID orderId, UUID itemId, BigDecimal quantity,
			CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		Map<String, Object> body = Map.of(
				"items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", quantity))
		);
		ready.countDown();
		start.await();
		return restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
	}

	@SuppressWarnings("unchecked")
	private String raceCreateOrderAndGetNumber(String token, CountDownLatch ready, CountDownLatch start)
			throws InterruptedException {
		Map<String, Object> body = Map.of(
				"supplierExternalId", SUPPLIER_AGROFERTIL,
				"paymentTerms", "Contado",
				"notes", "Concurrent order creation fixture",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000"), "unitCost", new BigDecimal("3500.0000"))
				)
		);
		ready.countDown();
		start.await();
		Map<String, Object> response = restClient.post()
				.uri("/api/purchases/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Map.class);
		assertThat(response).isNotNull();
		return (String) response.get("orderNumber");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createOrder(String token, UUID productId, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"supplierExternalId", SUPPLIER_AGROFERTIL,
				"paymentTerms", "Contado",
				"notes", "Order concurrency fixture",
				"items", List.of(
						Map.of("productExternalId", productId, "quantity", quantity, "unitCost", new BigDecimal("60000.0000"))
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

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	private <T> T join(Future<T> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
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
