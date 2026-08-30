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
 * Proves R-11/R-12/T-01 against a real PostgreSQL 17 (Testcontainers): dispatch applies
 * {@code TRANSFER_OUT} on the origin and increments the destination's {@code in_transit_stock}
 * with no Kardex row for the shift itself, and — {@code transfers} being the first real consumer
 * of {@code StockMutationPort} (design §11 trap 1) — a mid-dispatch failure on one item leaves
 * the transfer's state, both branches' balances and the Kardex untouched for every item, proving
 * dispatch is genuinely all-or-nothing rather than committed per item (tasks.md 3.1).
 *
 * <p>The forced failure is the real R-12 business rejection (insufficient origin stock on the
 * second item of a two-item dispatch), not a synthetic fixture like {@code KardexAtomicityIT}'s:
 * unlike a manual stock adjustment, a dispatch has no controllable "fail after N writes" hook to
 * copy without re-implementing {@code DispatchTransferService} in test source, and R-12 already
 * gives a production code path that fails deep inside the same loop {@code applyPlanLine} drives —
 * exercising the exact transactional boundary T-01 requires, through the real HTTP flow.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferDispatchAtomicityIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	// FERT-NPK-151515, seeded with 2500 KG at Medellín — comfortably dispatchable.
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// SEM-MAIZ-H300, seeded with only 12 bolsas at Medellín (02-seed-data.sql §5) — the item
	// engineered to fail R-12's stock check at dispatch time.
	private static final UUID PRODUCT_MAIZ = UUID.fromString("d0000000-0000-0000-0000-000000000003");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String caliToken;
	private String medellinToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		caliToken = token("gerente.cali");
		medellinToken = token("gerente.medellin");
	}

	@Test
	void dispatchAppliesTransferOutOnOriginAndIncrementsDestinationInTransitWithNoKardexRowForTheShift() {
		BigDecimal quantity = new BigDecimal("50.0000");
		BigDecimal originStockBefore = currentStock(BRANCH_MEDELLIN, PRODUCT_NPK);
		BigDecimal destinationInTransitBefore = inTransitStock(BRANCH_CALI, PRODUCT_NPK);

		UUID transferId = requestTransfer(caliToken, BRANCH_MEDELLIN, List.of(item(PRODUCT_NPK, quantity)));
		Map<String, Object> detail = detailOf(transferId, medellinToken);
		UUID itemId = itemExternalId(detail, PRODUCT_NPK);
		approve(medellinToken, transferId, List.of(approvedLine(itemId, quantity)));

		ResponseEntity<String> dispatchResponse = dispatch(medellinToken, transferId,
				List.of(dispatchLine(itemId, quantity)));

		assertThat(dispatchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(currentStock(BRANCH_MEDELLIN, PRODUCT_NPK)).isEqualByComparingTo(originStockBefore.subtract(quantity));
		assertThat(inTransitStock(BRANCH_CALI, PRODUCT_NPK))
				.isEqualByComparingTo(destinationInTransitBefore.add(quantity));
		assertThat(kardexRowCount(transferId, "TRANSFER_OUT")).isEqualTo(1L);
		// R-16/P-05/P-06: shiftInTransit writes no Kardex row of any kind for this reference.
		assertThat(kardexRowCountForReference(transferId)).isEqualTo(1L);
	}

	@Test
	void aForcedMidDispatchFailureLeavesStateBalancesAndKardexUnchangedForEveryItem() {
		BigDecimal goodQuantity = new BigDecimal("50.0000");
		// Exceeds Medellín's 12-bolsa balance for this product, forcing R-12's insufficient_stock
		// deep inside the same per-item loop that already applied the first item's movement.
		BigDecimal impossibleQuantity = new BigDecimal("9999.0000");

		BigDecimal npkStockBefore = currentStock(BRANCH_MEDELLIN, PRODUCT_NPK);
		BigDecimal npkInTransitBefore = inTransitStock(BRANCH_CALI, PRODUCT_NPK);
		BigDecimal maizStockBefore = currentStock(BRANCH_MEDELLIN, PRODUCT_MAIZ);
		BigDecimal maizInTransitBefore = inTransitStock(BRANCH_CALI, PRODUCT_MAIZ);

		UUID transferId = requestTransfer(caliToken, BRANCH_MEDELLIN,
				List.of(item(PRODUCT_NPK, goodQuantity), item(PRODUCT_MAIZ, impossibleQuantity)));
		Map<String, Object> detail = detailOf(transferId, medellinToken);
		UUID npkItemId = itemExternalId(detail, PRODUCT_NPK);
		UUID maizItemId = itemExternalId(detail, PRODUCT_MAIZ);
		approve(medellinToken, transferId,
				List.of(approvedLine(npkItemId, goodQuantity), approvedLine(maizItemId, impossibleQuantity)));

		ResponseEntity<ErrorBody> dispatchResponse = dispatchRaw(medellinToken, transferId,
				List.of(dispatchLine(npkItemId, goodQuantity), dispatchLine(maizItemId, impossibleQuantity)));

		assertThat(dispatchResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(dispatchResponse.getBody()).isNotNull();
		assertThat(dispatchResponse.getBody().code()).isEqualTo("insufficient_stock");

		assertThat(currentStock(BRANCH_MEDELLIN, PRODUCT_NPK)).isEqualByComparingTo(npkStockBefore);
		assertThat(inTransitStock(BRANCH_CALI, PRODUCT_NPK)).isEqualByComparingTo(npkInTransitBefore);
		assertThat(currentStock(BRANCH_MEDELLIN, PRODUCT_MAIZ)).isEqualByComparingTo(maizStockBefore);
		assertThat(inTransitStock(BRANCH_CALI, PRODUCT_MAIZ)).isEqualByComparingTo(maizInTransitBefore);
		assertThat(kardexRowCountForReference(transferId)).isZero();
		assertThat(statusOf(transferId, medellinToken)).isEqualTo("IN_PREPARATION");
	}

	// --- helpers -----------------------------------------------------------

	private UUID requestTransfer(String token, UUID originBranchExternalId, List<Map<String, Object>> items) {
		Map<String, Object> body = Map.of("originBranchExternalId", originBranchExternalId, "priority", "STANDARD",
				"notes", "atomicity fixture", "items", items);
		@SuppressWarnings("unchecked")
		Map<String, Object> response = restClient.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
		assertThat(response).isNotNull();
		return UUID.fromString((String) response.get("externalId"));
	}

	private Map<String, Object> item(UUID productExternalId, BigDecimal requestedQuantity) {
		return Map.of("productExternalId", productExternalId, "requestedQuantity", requestedQuantity);
	}

	private Map<String, Object> approvedLine(UUID itemExternalId, BigDecimal approvedQuantity) {
		return Map.of("itemExternalId", itemExternalId, "approvedQuantity", approvedQuantity);
	}

	private Map<String, Object> dispatchLine(UUID itemExternalId, BigDecimal dispatchedQuantity) {
		return Map.of("itemExternalId", itemExternalId, "dispatchedQuantity", dispatchedQuantity);
	}

	private void approve(String token, UUID transferId, List<Map<String, Object>> items) {
		restClient.post().uri("/api/transfers/{id}/approval", transferId).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(Map.of("items", items)).retrieve()
				.toBodilessEntity();
	}

	private ResponseEntity<String> dispatch(String token, UUID transferId, List<Map<String, Object>> items) {
		Map<String, Object> body = Map.of("carrierName", "Servientrega", "items", items);
		return restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().toEntity(String.class);
	}

	private ResponseEntity<ErrorBody> dispatchRaw(String token, UUID transferId, List<Map<String, Object>> items) {
		Map<String, Object> body = Map.of("carrierName", "Servientrega", "items", items);
		return restClient.post().uri("/api/transfers/{id}/dispatch", transferId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body)
				.retrieve().onStatus(status -> true, (req, res) -> {
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
	private UUID itemExternalId(Map<String, Object> detail, UUID productExternalId) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) detail.get("items");
		return items.stream().filter(i -> productExternalId.toString().equals(i.get("productExternalId")))
				.map(i -> UUID.fromString((String) i.get("externalId"))).findFirst()
				.orElseThrow(() -> new AssertionError("item not found for product " + productExternalId));
	}

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

	private long kardexRowCount(UUID transferExternalId, String movementType) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements WHERE reference_type = 'TRANSFER' AND reference_id = ? "
						+ "AND movement_type = ?",
				Long.class, transferExternalId.toString(), movementType);
		return count == null ? 0L : count;
	}

	private long kardexRowCountForReference(UUID transferExternalId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM kardex_movements WHERE reference_type = 'TRANSFER' AND reference_id = ?",
				Long.class, transferExternalId.toString());
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
