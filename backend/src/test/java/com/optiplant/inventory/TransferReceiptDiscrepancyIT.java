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
 * Proves R-16/R-17/R-18/R-20 against a real PostgreSQL 17 (Testcontainers): a partial receipt
 * (100 dispatched, 90 received) grants only the received quantity, drains the full dispatched
 * quantity from {@code in_transit_stock}, records the discrepancy, resolves to
 * {@code RECEIVED_WITH_DISCREPANCY} and raises one {@code TRANSFER_DISCREPANCY} alert per branch;
 * a full receipt resolves to {@code RECEIVED} with no alert (tasks.md 3.2). D-2's unit-cost
 * contract is proven by comparing the {@code TRANSFER_IN} row's {@code unit_cost} against the
 * matching {@code TRANSFER_OUT}'s.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferReceiptDiscrepancyIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");
	// FERT-NPK-151515, seeded with 5000 KG at Bogotá — comfortably dispatchable twice over.
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String bogotaToken;
	private String medellinToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = token("gerente.bogota");
		medellinToken = token("gerente.medellin");
	}

	@Test
	void aPartialReceiptGrantsOnlyWhatArrivedDrainsInTransitByTheFullDispatchAndAlertsBothBranches() {
		BigDecimal dispatched = new BigDecimal("100.0000");
		BigDecimal received = new BigDecimal("90.0000");
		BigDecimal discrepancy = new BigDecimal("10.0000");

		BigDecimal destinationStockBefore = currentStock(BRANCH_MEDELLIN, PRODUCT_NPK);
		BigDecimal destinationInTransitBefore = inTransitStock(BRANCH_MEDELLIN, PRODUCT_NPK);

		UUID transferId = requestApproveAndDispatch(dispatched);

		ResponseEntity<Map> receiptResponse = receive(medellinToken, transferId,
				List.of(receiptLine(itemExternalIdOf(transferId), received, "Bolsas dañadas en tránsito")));

		assertThat(receiptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = receiptResponse.getBody();
		assertThat(body).isNotNull();
		assertThat(body.get("status")).isEqualTo("RECEIVED_WITH_DISCREPANCY");

		assertThat(currentStock(BRANCH_MEDELLIN, PRODUCT_NPK))
				.isEqualByComparingTo(destinationStockBefore.add(received));
		// R-16: in_transit_stock drains by the full dispatched quantity, not the received one.
		assertThat(inTransitStock(BRANCH_MEDELLIN, PRODUCT_NPK)).isEqualByComparingTo(destinationInTransitBefore);

		Map<String, Object> item = firstItem(body);
		assertThat(new BigDecimal(item.get("discrepancyQuantity").toString())).isEqualByComparingTo(discrepancy);

		assertThat(discrepancyAlertCount(BRANCH_BOGOTA, transferId)).isEqualTo(1L);
		assertThat(discrepancyAlertCount(BRANCH_MEDELLIN, transferId)).isEqualTo(1L);

		assertThat(unitCost(transferId, "TRANSFER_IN")).isEqualByComparingTo(unitCost(transferId, "TRANSFER_OUT"));
	}

	@Test
	void aFullReceiptResolvesToReceivedWithNoAlert() {
		BigDecimal quantity = new BigDecimal("20.0000");

		UUID transferId = requestApproveAndDispatch(quantity);

		ResponseEntity<Map> receiptResponse = receive(medellinToken, transferId,
				List.of(receiptLine(itemExternalIdOf(transferId), quantity, null)));

		assertThat(receiptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> body = receiptResponse.getBody();
		assertThat(body).isNotNull();
		assertThat(body.get("status")).isEqualTo("RECEIVED");

		assertThat(discrepancyAlertCount(BRANCH_BOGOTA, transferId)).isZero();
		assertThat(discrepancyAlertCount(BRANCH_MEDELLIN, transferId)).isZero();
	}

	// --- setup helper --------------------------------------------------

	private UUID requestApproveAndDispatch(BigDecimal quantity) {
		UUID transferId = requestTransfer(medellinToken, BRANCH_BOGOTA, quantity);
		UUID itemId = itemExternalIdOf(transferId);
		approve(bogotaToken, transferId, itemId, quantity);
		dispatch(bogotaToken, transferId, itemId, quantity);
		return transferId;
	}

	// --- HTTP helpers ----------------------------------------------------

	@SuppressWarnings("unchecked")
	private UUID requestTransfer(String token, UUID originBranchExternalId, BigDecimal quantity) {
		Map<String, Object> body = Map.of("originBranchExternalId", originBranchExternalId, "priority", "STANDARD",
				"notes", "receipt discrepancy fixture", "items",
				List.of(Map.of("productExternalId", PRODUCT_NPK, "requestedQuantity", quantity)));
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

	private void dispatch(String token, UUID transferId, UUID itemId, BigDecimal dispatchedQuantity) {
		Map<String, Object> body = Map.of("carrierName", "Servientrega", "items",
				List.of(Map.of("itemExternalId", itemId, "dispatchedQuantity", dispatchedQuantity)));
		ResponseEntity<String> response = restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().toEntity(String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private Map<String, Object> receiptLine(UUID itemId, BigDecimal receivedQuantity, String discrepancyReason) {
		if (discrepancyReason == null) {
			return Map.of("itemExternalId", itemId, "receivedQuantity", receivedQuantity);
		}
		return Map.of("itemExternalId", itemId, "receivedQuantity", receivedQuantity, "discrepancyReason",
				discrepancyReason);
	}

	@SuppressWarnings("unchecked")
	private ResponseEntity<Map> receive(String token, UUID transferId, List<Map<String, Object>> items) {
		Map<String, Object> body = Map.of("items", items);
		return restClient.post().uri("/api/transfers/{id}/receipt", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().toEntity(Map.class);
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(UUID transferId) {
		Map<String, Object> detail = restClient.get().uri("/api/transfers/{id}", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken).retrieve().body(Map.class);
		assertThat(detail).isNotNull();
		return UUID.fromString((String) firstItem(detail).get("externalId"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> firstItem(Map<String, Object> detail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
		assertThat(items).isNotEmpty();
		return items.get(0);
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

	private BigDecimal unitCost(UUID transferExternalId, String movementType) {
		return jdbcTemplate.queryForObject(
				"SELECT unit_cost FROM kardex_movements WHERE reference_type = 'TRANSFER' AND reference_id = ? "
						+ "AND movement_type = ?",
				BigDecimal.class, transferExternalId.toString(), movementType);
	}

	private long discrepancyAlertCount(UUID branchExternalId, UUID transferExternalId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM system_alerts sa JOIN branches b ON b.id = sa.branch_id "
						+ "WHERE b.external_id = ? AND sa.alert_type = 'TRANSFER_DISCREPANCY' AND sa.title = ?",
				Long.class, branchExternalId, "TRANSFER_DISCREPANCY:" + transferExternalId);
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
