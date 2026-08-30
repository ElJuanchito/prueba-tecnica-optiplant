package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Branch isolation for {@code transfers} and {@code logistics} against a real PostgreSQL 17
 * (Testcontainers) — contract §5 (tasks.md 3.4). Named {@code TransferBranchIsolationIT}, not
 * {@code BranchIsolationIT} (from {@code iam}) or {@code InventoryBranchIsolationIT} (from
 * {@code inventory}), which already exist in this package (design §11 trap 3).
 *
 * <p>Uses the demonstrative in-transit transfer seeded in {@code 02-seed-data.sql} §8
 * ({@code TRF-2026-0001}, Bogotá &rarr; Cali) so a third branch (Medellín) and the wrong side of
 * the same transfer can be exercised without creating and dispatching a fresh one. Every write
 * body below carries one syntactically valid (if operationally meaningless) item line so bean
 * validation's {@code @NotEmpty} never intercepts the request before the access check does.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferBranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";
	// TRF-2026-0001: origin Bogotá, destination Cali, IN_TRANSIT (02-seed-data.sql §8).
	private static final UUID SEEDED_TRANSFER = UUID.fromString("30000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	// --- §5 visibility: a third branch sees transfer_not_found -----------

	@Test
	void aThirdBranchGetsTransferNotFoundOnDetail() {
		String medellin = token("gerente.medellin");

		ResponseEntity<ErrorBody> response = restClient.get().uri("/api/transfers/{id}", SEEDED_TRANSFER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + medellin).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("transfer_not_found");
	}

	// --- §5 side: the destination cannot dispatch, the origin cannot receive --

	@Test
	void theDestinationBranchCannotDispatch() {
		String cali = token("gerente.cali"); // destination, not origin

		ResponseEntity<ErrorBody> response = restClient.post().uri("/api/transfers/{id}/dispatch", SEEDED_TRANSFER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + cali).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("carrierName", "Servientrega", "items",
						List.of(Map.of("itemExternalId", UUID.randomUUID(), "dispatchedQuantity", 1))))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);

		// Already IN_TRANSIT, so R-01 would refuse it too; the side check (R-10) runs first
		// (TransferAccessPolicy.assertSide before the state machine), so cross-branch wins.
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("cross_branch_access_denied");
	}

	@Test
	void theOriginBranchCannotReceive() {
		String bogota = token("gerente.bogota"); // origin, not destination

		ResponseEntity<ErrorBody> response = restClient.post().uri("/api/transfers/{id}/receipt", SEEDED_TRANSFER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogota).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", UUID.randomUUID(), "receivedQuantity", 1))))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("cross_branch_access_denied");
	}

	// --- §5: OPERATOR denied approval, cancellation, monitor and report ----

	@Test
	void operatorIsDeniedApproval() {
		String operator = token("operador.bogota");

		ResponseEntity<Void> response = restClient.post().uri("/api/transfers/{id}/approval", SEEDED_TRANSFER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operator).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", UUID.randomUUID(), "approvedQuantity", 1))))
				.retrieve().onStatus(status -> true, (req, res) -> {
				}).toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void operatorIsDeniedCancellation() {
		String operator = token("operador.bogota");

		ResponseEntity<Void> response = restClient.post().uri("/api/transfers/{id}/cancellation", SEEDED_TRANSFER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operator).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", "not allowed")).retrieve().onStatus(status -> true, (req, res) -> {
				}).toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void operatorIsDeniedTheMonitor() {
		String operator = token("operador.bogota");

		ResponseEntity<Void> response = restClient.get().uri("/api/logistics/transfers/active")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operator).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void operatorIsDeniedTheComplianceReport() {
		String operator = token("operador.bogota");

		ResponseEntity<Void> response = restClient.get()
				.uri(builder -> builder.path("/api/logistics/compliance")
						.queryParam("from", Instant.now().minusSeconds(86400)).queryParam("to", Instant.now()).build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operator).retrieve()
				.onStatus(status -> true, (req, res) -> {
				}).toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	// --- helpers -----------------------------------------------------------

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
