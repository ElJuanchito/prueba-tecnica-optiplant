package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Turns contract §9's read-latency claim into a measurement (S8 task 8.15),
 * against a real PostgreSQL 17 (Testcontainers). Seeds ~10 000 products in one
 * bulk {@code INSERT … SELECT generate_series} — not 10 000 API calls — then
 * asserts <b>both</b>:
 *
 * <ol>
 *   <li><b>(a)</b> a <em>contains</em> search ({@code q=npk}, page size 20) stays
 *       under a generous wall-clock threshold — warmed-up median over 25 runs, a
 *       200 ms ceiling with headroom so it fails on a real regression, not CI
 *       jitter;</li>
 *   <li><b>(b)</b> {@code EXPLAIN} for that predicate reports a <b>sequential
 *       scan</b> on {@code products} — what design §8.4 and the corrected contract
 *       §9 now declare (a leading {@code %} wildcard makes any B-Tree index
 *       unusable). {@code idx_products_sku} serves the SKU-equality uniqueness
 *       check, not this search.</li>
 * </ol>
 *
 * <p>Failing (a) is {@code DT-08}'s documented trigger — the response is to pay
 * that debt ({@code pg_trgm} + GIN indexes), never to weaken this assertion.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSearchPerformanceIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final int SEED_PRODUCTS = 10_000;
	private static final long LATENCY_CEILING_MS = 200L;
	private static final int WARMUP_RUNS = 5;
	private static final int MEASURED_RUNS = 25;

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;

	@BeforeAll
	void seedTenThousandProducts() {
		jdbcTemplate.update("DELETE FROM products WHERE sku LIKE 'PERF-%'");
		jdbcTemplate.update("""
				INSERT INTO products (category_id, sku, name, base_unit)
				SELECT ((g - 1) % 4) + 1,
				       'PERF-' || LPAD(g::text, 6, '0'),
				       'Perf product ' || g,
				       'KG'
				FROM generate_series(1, ?) AS g
				""", SEED_PRODUCTS);

		Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM products", Integer.class);
		assertThat(total).as("the search must be measured at the contracted volumetry").isGreaterThanOrEqualTo(10_000);
	}

	@AfterAll
	void removeSeededProducts() {
		jdbcTemplate.update("DELETE FROM products WHERE sku LIKE 'PERF-%'");
	}

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
	}

	// --- 8.15 (a) ------------------------------------------------------

	@Test
	void aContainsSearchStaysUnderTheLatencyCeilingAtTenThousandProducts() {
		for (int i = 0; i < WARMUP_RUNS; i++) {
			runSearch();
		}

		List<Long> millis = new ArrayList<>(MEASURED_RUNS);
		for (int i = 0; i < MEASURED_RUNS; i++) {
			long start = System.nanoTime();
			int hits = runSearch();
			millis.add((System.nanoTime() - start) / 1_000_000L);
			assertThat(hits).as("q=npk must still find the seeded FERT-NPK-151515 product").isGreaterThanOrEqualTo(1);
		}
		millis.sort(Long::compareTo);
		long median = millis.get(millis.size() / 2);
		long p95 = millis.get((int) Math.ceil(0.95 * millis.size()) - 1);

		System.out.printf(
				"[8.15] contains-search q=npk size=20 over %d products, %d measured runs: median=%dms p95=%dms max=%dms%n",
				SEED_PRODUCTS, MEASURED_RUNS, median, p95, millis.get(millis.size() - 1));

		assertThat(median)
				.as("warmed-up median search latency must stay under %dms (contract §9 / RNF-PER-01); "
						+ "exceeding it is DT-08's trigger, not a reason to weaken this assertion", LATENCY_CEILING_MS)
				.isLessThan(LATENCY_CEILING_MS);
	}

	// --- 8.15 (b) ------------------------------------------------------

	@Test
	void explainReportsASequentialScanOnProductsForTheContainsPredicate() {
		// The predicate the repository's JPQL `search` compiles to: a case-insensitive
		// contains-match on sku/name. No ORDER BY / LIMIT here so nothing tempts the
		// planner toward idx_products_sku — the point is that the leading-wildcard
		// LIKE itself must touch every row (design §8.4, contract §9).
		List<Map<String, Object>> plan = jdbcTemplate.queryForList("""
				EXPLAIN
				SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id
				WHERE (LOWER(p.sku) LIKE '%npk%' OR LOWER(p.name) LIKE '%npk%')
				  AND p.is_active = TRUE
				""");
		String planText = plan.stream().map(row -> String.valueOf(row.get("QUERY PLAN")))
				.collect(Collectors.joining("\n"));

		System.out.println("[8.15] EXPLAIN for the contains predicate:\n" + planText);

		assertThat(planText)
				.as("the free-text search must resolve by sequential scan on products (design §8.4, contract §9)")
				.containsIgnoringCase("Seq Scan on products");
	}

	// --- helpers ----------------------------------------------------

	@SuppressWarnings("unchecked")
	private int runSearch() {
		Map<String, Object> page = restClient.get().uri("/api/catalog/products?q=npk&size=20")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken).retrieve().body(Map.class);
		assertThat(page).isNotNull();
		return ((List<Object>) page.get("content")).size();
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
