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
 * Proves R-01/R-22 against a real PostgreSQL 17 (Testcontainers): dispatch attempted from
 * {@code REQUESTED} (skipping approval) and cancellation attempted from {@code IN_TRANSIT} are
 * both refused with {@code 409 invalid_transfer_state}, and neither touches any balance
 * (tasks.md 3.3). Every legal/illegal transition pair is already enumerated exhaustively by the
 * unit {@code TransferStateMachineTest} (S1); this class only proves the two transitions a real
 * HTTP call — and a real pessimistic lock acquisition — can reach.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferStateMachineIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	// RIEGO-MANG-16MM, seeded at every branch (02-seed-data.sql §5).
	private static final UUID PRODUCT_RIEGO = UUID.fromString("d0000000-0000-0000-0000-000000000005");

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
	void dispatchFromRequestedIsRefusedAndTouchesNoBalance() {
		BigDecimal quantity = new BigDecimal("5.0000");
		BigDecimal originStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_RIEGO);

		UUID transferId = requestTransfer(caliToken, BRANCH_BOGOTA, quantity);
		UUID itemId = itemExternalIdOf(transferId, bogotaToken);

		ResponseEntity<ErrorBody> response = dispatch(bogotaToken, transferId, itemId, quantity);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_transfer_state");
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_RIEGO)).isEqualByComparingTo(originStockBefore);
		assertThat(statusOf(transferId, bogotaToken)).isEqualTo("REQUESTED");
	}

	@Test
	void cancellationFromInTransitIsRefusedAndTouchesNoBalance() {
		BigDecimal quantity = new BigDecimal("5.0000");
		BigDecimal originStockBefore = currentStock(BRANCH_BOGOTA, PRODUCT_RIEGO);
		BigDecimal destinationInTransitBefore = inTransitStock(BRANCH_CALI, PRODUCT_RIEGO);

		UUID transferId = requestTransfer(caliToken, BRANCH_BOGOTA, quantity);
		UUID itemId = itemExternalIdOf(transferId, bogotaToken);
		approve(bogotaToken, transferId, itemId, quantity);
		ResponseEntity<String> dispatched = dispatchOk(bogotaToken, transferId, itemId, quantity);
		assertThat(dispatched.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<ErrorBody> response = cancel(caliToken, transferId, "no longer needed");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_transfer_state");
		assertThat(currentStock(BRANCH_BOGOTA, PRODUCT_RIEGO))
				.isEqualByComparingTo(originStockBefore.subtract(quantity));
		assertThat(inTransitStock(BRANCH_CALI, PRODUCT_RIEGO))
				.isEqualByComparingTo(destinationInTransitBefore.add(quantity));
		assertThat(statusOf(transferId, bogotaToken)).isEqualTo("IN_TRANSIT");
	}

	// --- HTTP helpers ----------------------------------------------------

	@SuppressWarnings("unchecked")
	private UUID requestTransfer(String token, UUID originBranchExternalId, BigDecimal quantity) {
		Map<String, Object> body = Map.of("originBranchExternalId", originBranchExternalId, "priority", "STANDARD",
				"notes", "state machine fixture", "items",
				List.of(Map.of("productExternalId", PRODUCT_RIEGO, "requestedQuantity", quantity)));
		Map<String, Object> response = restClient.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
		assertThat(response).isNotNull();
		return UUID.fromString((String) response.get("externalId"));
	}

	private void approve(String token, UUID transferId, UUID itemId, BigDecimal approvedQuantity) {
		Map<String, Object> body = Map.of("items",
				List.of(Map.of("itemExternalId", itemId, "approvedQuantity", approvedQuantity)));
		restClient.post().uri("/api/transfers/{id}/approval", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().toBodilessEntity();
	}

	private ResponseEntity<String> dispatchOk(String token, UUID transferId, UUID itemId, BigDecimal quantity) {
		return restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("carrierName", "Servientrega", "items",
						List.of(Map.of("itemExternalId", itemId, "dispatchedQuantity", quantity))))
				.retrieve().toEntity(String.class);
	}

	private ResponseEntity<ErrorBody> dispatch(String token, UUID transferId, UUID itemId, BigDecimal quantity) {
		return restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("carrierName", "Servientrega", "items",
						List.of(Map.of("itemExternalId", itemId, "dispatchedQuantity", quantity))))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
	}

	private ResponseEntity<ErrorBody> cancel(String token, UUID transferId, String reason) {
		return restClient.post().uri("/api/transfers/{id}/cancellation", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", reason)).retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> detailOf(UUID transferId, String token) {
		Map<String, Object> body = restClient.get().uri("/api/transfers/{id}", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(Map.class);
		assertThat(body).isNotNull();
		return body;
	}

	private String statusOf(UUID transferId, String token) {
		return (String) detailOf(transferId, token).get("status");
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(UUID transferId, String token) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) detailOf(transferId, token).get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	// --- database helpers --------------------------------------------------

	private BigDecimal currentStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT current_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
						+ "JOIN products p ON p.id = bi.product_id WHERE b.external_id = ? AND p.external_id = ?",
				BigDecimal.class, branchExternalId, productExternalId);
	}

	private BigDecimal inTransitStock(UUID branchExternalId, UUID productExternalId) {
		return jdbcTemplate.queryForObject(
				"SELECT in_transit_stock FROM branch_inventories bi JOIN branches b ON b.id = bi.branch_id "
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
