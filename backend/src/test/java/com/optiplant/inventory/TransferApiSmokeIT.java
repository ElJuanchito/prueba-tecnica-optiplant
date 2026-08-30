package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * One smoke assertion per read endpoint plus route CRUD (tasks.md 3.6): status, page-envelope
 * shape ({@code content}/{@code totalElements}/{@code page}/{@code size}), no numeric {@code id}
 * leaking anywhere (contract §7 "must not leak") and no raw F-1 {@code PRIORITY:} token escaping
 * {@code TransferMapper} (design §3.5). Business-rule depth belongs to the other {@code *IT}s in
 * this phase; this file only proves every endpoint is wired and shaped correctly.
 *
 * <p>Route <em>create</em> is exercised through its documented conflict outcome
 * (§7 {@code route_already_exists}): the three seeded branches already hold routes for all six
 * ordered pairs (02-seed-data.sql §7, {@code uq_route_pair} ignores {@code is_active}), so no
 * branch pair exists to create a fresh row without either adding a fourth branch or mutating
 * another test's fixture data. Update is proven with a same-value round trip (the pattern
 * {@code InventoryApiSmokeIT}'s {@code thresholdPut} already uses); deactivation is exercised on
 * the Cali&rarr;Medellín route, the only seeded pair no other {@code transfers`/`logistics` *IT}
 * in this phase creates a transfer against (so its permanent logical deactivation, R-24, carries
 * no cross-test risk).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";
	// TRF-2026-0001: origin Bogotá, destination Cali, IN_TRANSIT (02-seed-data.sql §8).
	private static final UUID SEEDED_TRANSFER = UUID.fromString("30000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");
	// Cali -> Medellín, STANDARD (02-seed-data.sql §7) — the only ordered pair no *IT in this
	// phase dispatches a transfer against.
	private static final UUID ROUTE_CALI_MEDELLIN = UUID.fromString("20000000-0000-0000-0000-000000000006");

	@LocalServerPort
	private int port;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void listTransfersReturnsAPagedEnvelopeWithNoNumericId() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = get("/api/transfers", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void transferDetailReturnsTheExpectedShapeWithNoRawPriorityToken() {
		String medellin = token("gerente.medellin");
		UUID transferId = requestTransfer(medellin, BRANCH_CALI, "an URGENT one-off restock");

		ResponseEntity<String> response = get("/api/transfers/" + transferId, medellin);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"transferNumber\"").contains("\"status\"").contains("\"priority\":\"URGENT\"")
				.contains("\"originBranch\"").contains("\"destinationBranch\"").contains("\"items\"");
		assertNoNumericIdLeak(response.getBody());
		assertThat(response.getBody()).as("F-1 token must never leave TransferMapper").doesNotContain("PRIORITY:");
	}

	@Test
	void logisticsActiveTransfersReturnsAPagedEnvelope() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = get("/api/logistics/transfers/active", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void logisticsComplianceReturnsAPagedEnvelope() {
		String token = token("admin.corp");

		ResponseEntity<String> response = restClient.get()
				.uri(builder -> builder.path("/api/logistics/compliance")
						.queryParam("from", Instant.now().minusSeconds(30L * 86_400)).queryParam("to", Instant.now())
						.build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void routeListingReturnsAPagedEnvelope() {
		String token = token("admin.corp");

		ResponseEntity<String> response = get("/api/logistics/routes", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void creatingARouteForAnAlreadySeededPairIsRejected() {
		String token = token("admin.corp");

		ResponseEntity<ErrorBody> response = restClient.post().uri("/api/logistics/routes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("originBranchExternalId", BRANCH_BOGOTA, "destinationBranchExternalId", BRANCH_MEDELLIN,
						"estimatedDurationHours", new BigDecimal("5.00"), "transportCost", new BigDecimal("100000.00"),
						"priorityLevel", "STANDARD"))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("route_already_exists");
	}

	@Test
	void updatingARouteWithTheSameValuesReturnsTheUpdatedRoute() {
		String token = token("admin.corp");

		Map<String, Object> existing = routeFor(token, BRANCH_CALI, BRANCH_MEDELLIN);
		BigDecimal duration = new BigDecimal(existing.get("estimatedDurationHours").toString());
		BigDecimal cost = new BigDecimal(existing.get("transportCost").toString());
		String priority = (String) existing.get("priorityLevel");
		UUID routeId = UUID.fromString((String) existing.get("externalId"));

		ResponseEntity<String> response = restClient.put().uri("/api/logistics/routes/{id}", routeId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("estimatedDurationHours", duration, "transportCost", cost, "priorityLevel", priority))
				.retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"externalId\"").contains("\"estimatedDurationHours\"");
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void deactivatingARouteMarksItInactive() {
		String token = token("admin.corp");

		ResponseEntity<String> response = restClient.patch()
				.uri("/api/logistics/routes/{id}/deactivation", ROUTE_CALI_MEDELLIN)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"active\":false");
		assertNoNumericIdLeak(response.getBody());
	}

	// --- helpers -------------------------------------------------------

	@SuppressWarnings("unchecked")
	private Map<String, Object> routeFor(String token, UUID originExternalId, UUID destinationExternalId) {
		Map<String, Object> page = restClient.get().uri("/api/logistics/routes?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(Map.class);
		assertThat(page).isNotNull();
		List<Map<String, Object>> content = (List<Map<String, Object>>) page.get("content");
		return content.stream()
				.filter(r -> originExternalId.toString().equals(((Map<String, Object>) r.get("originBranch")).get("externalId"))
						&& destinationExternalId.toString()
								.equals(((Map<String, Object>) r.get("destinationBranch")).get("externalId")))
				.findFirst().orElseThrow(() -> new AssertionError("seeded route not found"));
	}

	@SuppressWarnings("unchecked")
	private UUID requestTransfer(String token, UUID originBranchExternalId, String notes) {
		UUID productRiego = UUID.fromString("d0000000-0000-0000-0000-000000000005");
		Map<String, Object> body = Map.of("originBranchExternalId", originBranchExternalId, "priority", "URGENT",
				"notes", notes, "items",
				List.of(Map.of("productExternalId", productRiego, "requestedQuantity", new BigDecimal("1.0000"))));
		Map<String, Object> response = restClient.post().uri("/api/transfers").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
		assertThat(response).isNotNull();
		return UUID.fromString((String) response.get("externalId"));
	}

	private static void assertPagedEnvelopeShape(String body) {
		assertThat(body).contains("\"content\"").contains("\"totalElements\"").contains("\"page\"")
				.contains("\"size\"");
	}

	/** contract §7 "must not leak": no field literally named {@code id} — only {@code externalId}
	 *  and its {@code *ExternalId} variants may appear. */
	private static void assertNoNumericIdLeak(String body) {
		assertThat(body).doesNotContain("\"id\":");
	}

	private ResponseEntity<String> get(String path, String token) {
		return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve()
				.toEntity(String.class);
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
