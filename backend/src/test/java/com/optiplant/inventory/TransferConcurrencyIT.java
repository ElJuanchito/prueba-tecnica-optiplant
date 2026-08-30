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
 * R-12/T-02 and D-3 against a real PostgreSQL 17 (Testcontainers, tasks.md 3.5): two concurrent
 * dispatches racing for the same {@code branch_inventories} row serialize on its {@code FOR
 * UPDATE} lock — exactly one wins, stock never goes negative, neither response is a {@code 500} —
 * and two simultaneous transfer requests never collide on the year-scoped advisory lock, each
 * receiving its own {@code transfer_number} (§6.2).
 *
 * <p>Dedicated to FUNG-BIO-TRICH (product 4) sent from Cali — no other {@code *IT} in this
 * default Spring context mutates that branch/product pair, so the "exactly one wins, balance
 * lands at zero" assertion does not depend on execution order against other test classes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferConcurrencyIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	// FUNG-BIO-TRICH, seeded at Cali (02-seed-data.sql §5) and untouched by any other transfers *IT.
	private static final UUID PRODUCT_TRICHODERMA = UUID.fromString("d0000000-0000-0000-0000-000000000004");

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
	void twoConcurrentDispatchesOfTheFullBalanceSerializeAndOnlyOneSucceeds() throws Exception {
		BigDecimal target = currentStock(BRANCH_CALI, PRODUCT_TRICHODERMA);
		assertThat(target).as("seeded balance must be positive for this probe to be meaningful")
				.isGreaterThan(BigDecimal.ZERO);

		UUID transferA = requestApproved(target);
		UUID transferB = requestApproved(target);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<ResponseEntity<ErrorBody>>> tasks = List.of(
				() -> raceDispatch(transferA, target, ready, start), () -> raceDispatch(transferB, target, ready, start));

		List<Future<ResponseEntity<ErrorBody>>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<ResponseEntity<ErrorBody>> results = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<HttpStatusCode> statuses = results.stream().map(ResponseEntity::getStatusCode).toList();
		assertThat(statuses).as("no concurrent dispatch may ever surface a 500")
				.noneMatch(HttpStatusCode::is5xxServerError);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.OK)).count())
				.as("exactly one concurrent dispatch must win the row lock").isEqualTo(1);
		assertThat(statuses.stream().filter(s -> s.equals(HttpStatus.CONFLICT)).count())
				.as("exactly one concurrent dispatch must lose with 409").isEqualTo(1);

		ResponseEntity<ErrorBody> loser = results.stream().filter(r -> r.getStatusCode().equals(HttpStatus.CONFLICT))
				.findFirst().orElseThrow();
		assertThat(loser.getBody()).isNotNull();
		assertThat(loser.getBody().code()).isEqualTo("insufficient_stock");

		assertThat(currentStock(BRANCH_CALI, PRODUCT_TRICHODERMA))
				.as("RN-01: current_stock must never go negative, and the winner drove it to zero")
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void twoSimultaneousTransferRequestsNeverCollideOnTheAdvisoryLockAndGetDistinctNumbers() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Callable<String>> tasks = List.of(() -> raceRequestTransferNumber(ready, start),
				() -> raceRequestTransferNumber(ready, start));

		List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
		ready.await();
		start.countDown();

		List<String> transferNumbers = futures.stream().map(this::join).toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(transferNumbers).hasSize(2);
		assertThat(transferNumbers.get(0)).matches("TRF-\\d{4}-\\d{4,}");
		assertThat(transferNumbers.get(1)).matches("TRF-\\d{4}-\\d{4,}");
		assertThat(transferNumbers.get(0)).isNotEqualTo(transferNumbers.get(1));
	}

	// --- setup / race helpers -----------------------------------------------

	@SuppressWarnings("unchecked")
	private UUID requestApproved(BigDecimal quantity) {
		Map<String, Object> body = Map.of("originBranchExternalId", BRANCH_CALI, "priority", "STANDARD", "notes",
				"concurrency fixture", "items",
				List.of(Map.of("productExternalId", PRODUCT_TRICHODERMA, "requestedQuantity", quantity)));
		Map<String, Object> response = restClient.post().uri("/api/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(Map.class);
		assertThat(response).isNotNull();
		UUID transferId = UUID.fromString((String) response.get("externalId"));
		List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
		UUID itemId = UUID.fromString((String) items.get(0).get("externalId"));

		restClient.post().uri("/api/transfers/{id}/approval", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", itemId, "approvedQuantity", quantity)))).retrieve()
				.toBodilessEntity();
		return transferId;
	}

	private ResponseEntity<ErrorBody> raceDispatch(UUID transferId, BigDecimal quantity, CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		UUID itemId = itemExternalIdOf(transferId);
		ready.countDown();
		start.await();
		Map<String, Object> body = Map.of("carrierName", "Servientrega", "items",
				List.of(Map.of("itemExternalId", itemId, "dispatchedQuantity", quantity)));
		return restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
	}

	private String raceRequestTransferNumber(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		Map<String, Object> body = Map.of("originBranchExternalId", BRANCH_CALI, "priority", "STANDARD", "notes",
				"advisory lock fixture", "items",
				List.of(Map.of("productExternalId", PRODUCT_TRICHODERMA, "requestedQuantity", new BigDecimal("1.0000"))));
		@SuppressWarnings("unchecked")
		Map<String, Object> response = restClient.post().uri("/api/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken).contentType(MediaType.APPLICATION_JSON)
				.body(body).retrieve().body(Map.class);
		assertThat(response).isNotNull();
		return (String) response.get("transferNumber");
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(UUID transferId) {
		Map<String, Object> detail = restClient.get().uri("/api/transfers/{id}", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken).retrieve().body(Map.class);
		assertThat(detail).isNotNull();
		List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
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
