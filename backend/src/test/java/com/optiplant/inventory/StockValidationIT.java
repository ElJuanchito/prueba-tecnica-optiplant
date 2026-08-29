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
 * R-11 / RN-01 / T-02 against a real PostgreSQL 17 (Testcontainers): two concurrent write-offs of
 * the same product+branch must serialize on the {@code FOR UPDATE} row lock, exactly one succeeds,
 * {@code current_stock} never goes negative, the loser gets {@code 409 insufficient_stock}, and
 * neither response is a {@code 500} (tasks.md 3.3).
 *
 * <p>Dedicated to BIO-FOL-AMINO (product 2) in Bogotá — no other {@code *IT} in this default
 * Spring context depletes that row, so the "exactly one wins, balance lands at zero" assertion
 * does not depend on execution order against other test classes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockValidationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID SEED_FOLIAR_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000002");

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
	void twoConcurrentWriteOffsOfTheFullBalanceSerializeAndOnlyOneSucceeds() throws Exception {
		// BRANCH_MANAGER, not OPERATOR: the setup step below needs to adjust the balance first
		// (R-10 denies OPERATOR adjustments), and write-offs are open to BRANCH_MANAGER too (§5) —
		// R-13's "OPERATOR is allowed write-offs" is InventoryRbacIT's concern, not this file's.
		String token = token("gerente.bogota");

		// Establish a known, positive balance regardless of what earlier test classes left behind
		// (R-08 forbids setting the count to the exact current value, so always target current + 1).
		BigDecimal beforeAdjust = rawStock();
		BigDecimal target = beforeAdjust.add(BigDecimal.ONE);
		adjust(token, target, "T-02 concurrency probe setup");
		assertThat(rawStock()).isEqualByComparingTo(target);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<ResponseEntity<ErrorBody>>> tasks = List.of(
				() -> raceWriteOff(token, target, ready, start), () -> raceWriteOff(token, target, ready, start));

		List<Future<ResponseEntity<ErrorBody>>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<ResponseEntity<ErrorBody>> results = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<HttpStatusCode> statuses = results.stream().map(ResponseEntity::getStatusCode).toList();
		assertThat(statuses).as("no request may ever surface a 500").noneMatch(HttpStatusCode::is5xxServerError);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CREATED)).count())
				.as("exactly one concurrent write-off must win the row lock").isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CONFLICT)).count())
				.as("exactly one concurrent write-off must lose with 409").isEqualTo(1);

		ResponseEntity<ErrorBody> loser = results.stream().filter(r -> r.getStatusCode().equals(HttpStatus.CONFLICT))
				.findFirst().orElseThrow();
		assertThat(loser.getBody()).isNotNull();
		assertThat(loser.getBody().code()).isEqualTo("insufficient_stock");

		assertThat(rawStock()).as("RN-01: current_stock must never go negative, and the winner drove it to zero")
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	private ResponseEntity<ErrorBody> raceWriteOff(String token, BigDecimal quantity, CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		return restClient.post().uri("/api/inventory/write-offs")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", SEED_FOLIAR_PRODUCT, "quantity", quantity, "reason",
						"T-02 concurrency probe"))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
	}

	private <T> T join(Future<T> future) {
		try {
			return future.get(10, TimeUnit.SECONDS);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void adjust(String token, BigDecimal countedQuantity, String reason) {
		restClient.post().uri("/api/inventory/adjustments")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("productExternalId", SEED_FOLIAR_PRODUCT, "countedQuantity", countedQuantity, "reason",
						reason))
				.retrieve().toBodilessEntity();
	}

	private BigDecimal rawStock() {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, BRANCH_BOGOTA, SEED_FOLIAR_PRODUCT);
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
