package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Branch isolation and authorization verification for analytics endpoints
 * (R-02, R-19, §5, §7).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalyticsBranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID BRANCH_MEDELLIN = UUID.fromString("b0000000-0000-0000-0000-000000000002");

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
	void branchManagerAndOperatorAreScopedToTheirOwnBranch() {
		String managerToken = token("gerente.bogota");
		String operatorToken = token("operador.bogota");

		for (String token : new String[]{managerToken, operatorToken}) {
			// Sales trend returns own branchExternalId
			ResponseEntity<Map> salesTrend = restClient.get()
					.uri("/api/analytics/dashboard/sales-trend")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.toEntity(Map.class);
			assertThat(salesTrend.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(salesTrend.getBody()).isNotNull();
			assertThat(salesTrend.getBody().get("branchExternalId")).isEqualTo(BRANCH_BOGOTA.toString());

			// Rotation returns 200
			ResponseEntity<Map> rotation = restClient.get()
					.uri("/api/analytics/dashboard/rotation")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.toEntity(Map.class);
			assertThat(rotation.getStatusCode()).isEqualTo(HttpStatus.OK);

			// Transfers summary returns 200
			ResponseEntity<Map> transfers = restClient.get()
					.uri("/api/analytics/dashboard/transfers")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.toEntity(Map.class);
			assertThat(transfers.getStatusCode()).isEqualTo(HttpStatus.OK);

			// Replenishment returns 200
			ResponseEntity<Map> replenishment = restClient.get()
					.uri("/api/analytics/replenishment")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.retrieve()
					.toEntity(Map.class);
			assertThat(replenishment.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@Test
	void nonAdminSendingBranchExternalIdGets403CrossBranchAccessDenied() {
		String managerToken = token("gerente.bogota");
		String operatorToken = token("operador.bogota");

		// BRANCH_MANAGER sending other branch
		ResponseEntity<ErrorBody> response1 = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?branchExternalId={id}", BRANCH_MEDELLIN)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response1.getBody()).isNotNull();
		assertThat(response1.getBody().code()).isEqualTo("cross_branch_access_denied");

		// OPERATOR sending other branch
		ResponseEntity<ErrorBody> response2 = restClient.get()
				.uri("/api/analytics/replenishment?branchExternalId={id}", BRANCH_MEDELLIN)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response2.getBody()).isNotNull();
		assertThat(response2.getBody().code()).isEqualTo("cross_branch_access_denied");

		// Non-ADMIN sending nonexistent branch gets cross_branch_access_denied before any lookup
		UUID nonExistentBranch = UUID.randomUUID();
		ResponseEntity<ErrorBody> response3 = restClient.get()
				.uri("/api/analytics/dashboard/transfers?branchExternalId={id}", nonExistentBranch)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response3.getBody()).isNotNull();
		assertThat(response3.getBody().code()).isEqualTo("cross_branch_access_denied");
	}

	@Test
	void corporateAdminOmittingBranchOnBranchDashboardGets403BranchContextRequired() {
		String adminToken = token("admin.corp");

		ResponseEntity<ErrorBody> salesTrend = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(salesTrend.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(salesTrend.getBody()).isNotNull();
		assertThat(salesTrend.getBody().code()).isEqualTo("branch_context_required");
		assertThat(salesTrend.getBody().message()).contains("/api/analytics/corporate/branches");

		ResponseEntity<ErrorBody> replenishment = restClient.get()
				.uri("/api/analytics/replenishment")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(replenishment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(replenishment.getBody()).isNotNull();
		assertThat(replenishment.getBody().code()).isEqualTo("branch_context_required");

		ResponseEntity<ErrorBody> transfers = restClient.get()
				.uri("/api/analytics/dashboard/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(transfers.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(transfers.getBody()).isNotNull();
		assertThat(transfers.getBody().code()).isEqualTo("branch_context_required");
	}

	@Test
	void corporateAdminProvidingBranchExternalIdGets200WithTargetBranchData() {
		String adminToken = token("admin.corp");

		ResponseEntity<Map> response = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?branchExternalId={id}", BRANCH_MEDELLIN)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().get("branchExternalId")).isEqualTo(BRANCH_MEDELLIN.toString());
	}

	@Test
	void corporateAdminProvidingUnknownBranchGets404BranchNotFound() {
		String adminToken = token("admin.corp");
		UUID unknownBranch = UUID.randomUUID();

		ResponseEntity<ErrorBody> response = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?branchExternalId={id}", unknownBranch)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("branch_not_found");
	}

	@Test
	void corporateBoardIsRestrictedToAdminOnly() {
		String managerToken = token("gerente.bogota");
		String operatorToken = token("operador.bogota");
		String adminToken = token("admin.corp");

		// Non-ADMIN gets 403 with uniform envelope
		ResponseEntity<ErrorBody> managerResp = restClient.get()
				.uri("/api/analytics/corporate/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(managerResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(managerResp.getBody()).isNotNull();
		assertThat(managerResp.getBody().code()).isEqualTo("forbidden");

		ResponseEntity<ErrorBody> operatorResp = restClient.get()
				.uri("/api/analytics/corporate/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);
		assertThat(operatorResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(operatorResp.getBody()).isNotNull();
		assertThat(operatorResp.getBody().code()).isEqualTo("forbidden");

		// ADMIN gets 200
		ResponseEntity<Map> adminResp = restClient.get()
				.uri("/api/analytics/corporate/branches")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(Map.class);
		assertThat(adminResp.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record ErrorBody(String code, String message) {}

	private record LoginRequestBody(String username, String password) {}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds,
			String role, String branchId, String branchName, String branchCode) {}
}
