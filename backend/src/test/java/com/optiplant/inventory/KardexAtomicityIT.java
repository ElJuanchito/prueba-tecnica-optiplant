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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Proves R-18/T-01 against a real PostgreSQL 17 (Testcontainers): the {@code branch_inventories}
 * balance and the matching {@code kardex_movements} row commit or fail together, and a full Kardex
 * replayed from {@code INITIAL_LOAD} equals {@code current_stock} exactly. Exercised through
 * {@code KardexAtomicityFixtureController} (test-source-only, copies the
 * {@code AuditAtomicityFixtureService} pattern — tasks.md 3.1), because the production endpoints
 * alone give no controllable "fail after both writes" hook.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KardexAtomicityIT {

	private static final String SEED_PASSWORD = "Password123!";
	// SEM-MAIZ-H300, seeded only in branch Bogotá (id 1) with an INITIAL_LOAD row (tasks.md 3.1).
	private static final UUID SEED_SEMILLA_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000003");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = accessTokenFor("operador.bogota");
	}

	@Test
	void aForcedFailureAfterBothWritesLeavesNeitherTheBalanceChangeNorTheKardexRow() {
		String referenceId = "kardex-atomicity-" + UUID.randomUUID();
		BigDecimal stockBefore = currentStock(SEED_SEMILLA_PRODUCT);
		long kardexRowsBefore = kardexRowCount(referenceId);

		ResponseEntity<String> response = invoke(referenceId, SEED_SEMILLA_PRODUCT, new BigDecimal("7.0000"), true);

		assertThat(response.getStatusCode().is5xxServerError()).isTrue();
		assertThat(currentStock(SEED_SEMILLA_PRODUCT)).isEqualByComparingTo(stockBefore);
		assertThat(kardexRowCount(referenceId)).isEqualTo(kardexRowsBefore);
	}

	@Test
	void aSuccessfulMutationCommitsBothTheBalanceAndTheKardexRowTogether() {
		String referenceId = "kardex-atomicity-" + UUID.randomUUID();
		BigDecimal stockBefore = currentStock(SEED_SEMILLA_PRODUCT);
		BigDecimal quantity = new BigDecimal("3.0000");

		ResponseEntity<String> response = invoke(referenceId, SEED_SEMILLA_PRODUCT, quantity, false);

		assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
		assertThat(currentStock(SEED_SEMILLA_PRODUCT)).isEqualByComparingTo(stockBefore.add(quantity));
		assertThat(kardexRowCount(referenceId)).isEqualTo(1L);
	}

	/**
	 * R-18: the full Kardex of a product in a branch, replayed from {@code INITIAL_LOAD}, equals
	 * {@code current_stock} exactly — verified as a chain of {@code previous_stock}/
	 * {@code resulting_stock} rather than re-deriving the sign convention in the test itself.
	 */
	@Test
	void theFullKardexReplayedFromInitialLoadEqualsCurrentStock() {
		// Add one more successful movement on top of whatever history already exists, so the chain
		// covers at least the seed's INITIAL_LOAD plus this run's own movement.
		invoke("kardex-atomicity-replay-" + UUID.randomUUID(), SEED_SEMILLA_PRODUCT, new BigDecimal("2.0000"), false);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT movement_type, previous_stock, resulting_stock FROM kardex_movements k "
						+ "JOIN branches b ON b.id = k.branch_id JOIN products p ON p.id = k.product_id "
						+ "WHERE b.external_id = ? AND p.external_id = ? ORDER BY k.created_at ASC, k.id ASC",
				UUID.fromString("b0000000-0000-0000-0000-000000000001"), SEED_SEMILLA_PRODUCT);

		assertThat(rows).isNotEmpty();
		assertThat(rows.get(0).get("movement_type")).isEqualTo("INITIAL_LOAD");
		assertThat((BigDecimal) rows.get(0).get("previous_stock")).isEqualByComparingTo(BigDecimal.ZERO);

		for (int i = 0; i < rows.size() - 1; i++) {
			BigDecimal resulting = (BigDecimal) rows.get(i).get("resulting_stock");
			BigDecimal nextPrevious = (BigDecimal) rows.get(i + 1).get("previous_stock");
			assertThat(nextPrevious).as("row %d's resulting_stock must chain into row %d's previous_stock", i, i + 1)
					.isEqualByComparingTo(resulting);
		}

		BigDecimal lastResulting = (BigDecimal) rows.get(rows.size() - 1).get("resulting_stock");
		assertThat(lastResulting).isEqualByComparingTo(currentStock(SEED_SEMILLA_PRODUCT));
	}

	private BigDecimal currentStock(UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, UUID.fromString("b0000000-0000-0000-0000-000000000001"), productExternalId);
	}

	private long kardexRowCount(String referenceId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements WHERE reference_type = 'kardex-atomicity-fixture' "
						+ "AND reference_id = ?",
				Long.class, referenceId);
		return count == null ? 0L : count;
	}

	private ResponseEntity<String> invoke(String referenceId, UUID productExternalId, BigDecimal quantity,
			boolean shouldFail) {
		return restClient.post()
				.uri(builder -> builder.path("/api/test/kardex-atomicity/{referenceId}")
						.queryParam("productExternalId", productExternalId).queryParam("quantity", quantity)
						.queryParam("shouldFail", shouldFail).build(referenceId))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {
				})
				.toEntity(String.class);
	}

	private String accessTokenFor(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}
}
