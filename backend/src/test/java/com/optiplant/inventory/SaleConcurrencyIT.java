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
 * R-05, T-02, and D-5 against a real PostgreSQL 17 (Testcontainers):
 * <ul>
 *   <li>Two concurrent sales racing over the last available unit serialize on row lock:
 *       exactly one succeeds, stock never goes negative, and neither returns a 500.</li>
 *   <li>Two simultaneous sales registrations produce two distinct {@code invoice_number}
 *       values under the advisory lock.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleConcurrencyIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");

	// RIEGO-MANG-16MM in Medellín (seeded with 40 units)
	private static final UUID PRODUCT_RIEGO = UUID.fromString("d0000000-0000-0000-0000-000000000005");
	// FERT-NPK-151515 in Bogotá (plenty of stock)
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String medellinToken;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		medellinToken = token("gerente.medellin");
		bogotaToken = token("gerente.bogota");
	}

	@Test
	void twoConcurrentSalesOverTheFullBalanceSerializeAndOnlyOneSucceeds() throws Exception {
		BigDecimal target = currentStock(BRANCH_MEDELLIN, PRODUCT_RIEGO);
		assertThat(target).as("seeded balance must be positive for this race to be meaningful")
				.isGreaterThan(BigDecimal.ZERO);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<ResponseEntity<ErrorBody>>> tasks = List.of(
				() -> raceRegisterSale(medellinToken, PRODUCT_RIEGO, target, "Cliente A", ready, start),
				() -> raceRegisterSale(medellinToken, PRODUCT_RIEGO, target, "Cliente B", ready, start)
		);

		List<Future<ResponseEntity<ErrorBody>>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<ResponseEntity<ErrorBody>> results = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<HttpStatusCode> statuses = results.stream().map(ResponseEntity::getStatusCode).toList();
		assertThat(statuses).as("no concurrent sale may ever surface a 500")
				.noneMatch(HttpStatusCode::is5xxServerError);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CREATED)).count())
				.as("exactly one concurrent sale must win").isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CONFLICT)).count())
				.as("exactly one concurrent sale must lose with 409").isEqualTo(1);

		ResponseEntity<ErrorBody> loser = results.stream()
				.filter(r -> r.getStatusCode().equals(HttpStatus.CONFLICT))
				.findFirst()
				.orElseThrow();
		assertThat(loser.getBody()).isNotNull();
		assertThat(loser.getBody().code()).isEqualTo("insufficient_stock");

		assertThat(currentStock(BRANCH_MEDELLIN, PRODUCT_RIEGO))
				.as("RN-01: current_stock must never go negative, and winner drove it to zero")
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void twoSimultaneousRegistrationsProduceDistinctInvoiceNumbers() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<String>> tasks = List.of(
				() -> raceRegisterAndGetInvoiceNumber(bogotaToken, "Cliente 1", ready, start),
				() -> raceRegisterAndGetInvoiceNumber(bogotaToken, "Cliente 2", ready, start)
		);

		List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<String> invoiceNumbers = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(invoiceNumbers).hasSize(2);
		assertThat(invoiceNumbers.get(0)).matches("VEN-\\d{4}-\\d{4,}");
		assertThat(invoiceNumbers.get(1)).matches("VEN-\\d{4}-\\d{4,}");
		assertThat(invoiceNumbers.get(0)).isNotEqualTo(invoiceNumbers.get(1));
	}

	private ResponseEntity<ErrorBody> raceRegisterSale(String token, UUID productExternalId, BigDecimal quantity,
			String customerName, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		Map<String, Object> body = Map.of(
				"customerName", customerName,
				"items", List.of(Map.of("productExternalId", productExternalId, "quantity", quantity))
		);
		ready.countDown();
		start.await();
		return restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
	}

	@SuppressWarnings("unchecked")
	private String raceRegisterAndGetInvoiceNumber(String token, String customerName,
			CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		Map<String, Object> body = Map.of(
				"customerName", customerName,
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);
		ready.countDown();
		start.await();
		Map<String, Object> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Map.class);
		assertThat(response).isNotNull();
		return (String) response.get("invoiceNumber");
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
