package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * One smoke assertion per read endpoint plus the threshold {@code PUT} (tasks.md 3.5): status,
 * page-envelope shape ({@code content}/{@code totalElements}/{@code page}/{@code size}), and no
 * numeric {@code id} leaking anywhere in the payload (contract §7 "must not leak"). Business-rule
 * depth (isolation, RBAC, concurrency, alert dedup) belongs to the other {@code *IT}s in this
 * phase; this file only proves every endpoint is wired and shaped correctly.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";
	// RIEGO-MANG-16MM: not otherwise mutated to a specific target by any other *IT in this shared
	// context, so its threshold PUT here is a safe, side-effect-free no-op (same value round-trip).
	private static final UUID SEED_RIEGO_PRODUCT = UUID.fromString("d0000000-0000-0000-0000-000000000005");

	@LocalServerPort
	private int port;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void stockEndpointReturnsAPagedEnvelopeWithNoNumericId() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = get("/api/inventory/stock", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void networkAvailabilityEndpointReturnsTheExpectedShape() {
		String token = token("admin.corp");

		ResponseEntity<String> response = get("/api/inventory/stock/" + SEED_RIEGO_PRODUCT + "/network", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("productExternalId").contains("branches").contains("networkTotal");
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void kardexEndpointReturnsAPagedEnvelope() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = get("/api/inventory/kardex", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	@Test
	void alertsEndpointReturnsAPagedEnvelope() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = get("/api/notifications/alerts", token);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(response.getBody());
		assertNoNumericIdLeak(response.getBody());
	}

	/**
	 * R-00 (RNF-PER-04): a page size above {@code MAX_PAGE_SIZE} must be refused with
	 * {@code 400 invalid_request}, never silently clamped. Both controllers'
	 * {@code resolveSize} implement this identically; neither had a test until DT-07's
	 * payment closed this gap.
	 */
	@Test
	void pageSizeAboveTheCapIsRejectedOnInventoryAndAlertsEndpoints() {
		String token = token("gerente.bogota");

		ResponseEntity<ErrorBody> stockResponse = getRaw("/api/inventory/stock?size=101", token);
		assertThat(stockResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(stockResponse.getBody()).isNotNull();
		assertThat(stockResponse.getBody().code()).isEqualTo("invalid_request");

		ResponseEntity<ErrorBody> alertsResponse = getRaw("/api/notifications/alerts?size=101", token);
		assertThat(alertsResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(alertsResponse.getBody()).isNotNull();
		assertThat(alertsResponse.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void thresholdPutReturnsTheUpdatedValue() {
		String token = token("gerente.bogota");

		ResponseEntity<String> response = restClient.put()
				.uri("/api/inventory/stock/{id}/threshold", SEED_RIEGO_PRODUCT)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
				.body(java.util.Map.of("minStockThreshold", new BigDecimal("15.0000"))).retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("productExternalId").contains("minStockThreshold");
		assertNoNumericIdLeak(response.getBody());
	}

	// --- helpers -------------------------------------------------------

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

	/** Like {@link #get}, but surfaces a non-2xx status as a body instead of throwing. */
	private ResponseEntity<ErrorBody> getRaw(String path, String token) {
		return restClient.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);
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

	private record ErrorBody(String code, String message) {
	}
}
