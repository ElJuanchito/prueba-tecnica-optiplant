package com.optiplant.inventory.analytics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.analytics.application.port.out.InventoryAnalyticsPort;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentSeverity;
import com.optiplant.inventory.analytics.domain.service.CoveragePolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL read adapter for inventory critical replenishment (design §4 Q-4, D-8).
 * Scans active products below threshold on the scoped branch via {@link JdbcClient} without JPA entities (P-01).
 * Default sort places {@code OUT_OF_STOCK} first (R-16).
 */
@Component
public class InventoryAnalyticsJdbcAdapter implements InventoryAnalyticsPort {

	private final JdbcClient jdbcClient;

	public InventoryAnalyticsJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public AnalyticsPage<ReplenishmentLine> replenishment(UUID branchExternalId,
			ReplenishmentSeverity severity, String sort, int page, int size) {
		String severityCondition = "";
		if (severity == ReplenishmentSeverity.OUT_OF_STOCK) {
			severityCondition = " AND bi.current_stock <= 0 ";
		} else if (severity == ReplenishmentSeverity.CRITICAL) {
			severityCondition = " AND bi.current_stock > 0 ";
		}

		String orderClause;
		if ("product".equalsIgnoreCase(sort)) {
			orderClause = "ORDER BY p.name ASC, p.sku ASC";
		} else if ("coverage".equalsIgnoreCase(sort)) {
			orderClause = "ORDER BY (CASE WHEN bi.current_stock <= 0 THEN 0 WHEN COALESCE(sales_30d.units_sold_30d, 0) = 0 THEN 999999999 ELSE (bi.current_stock * 30.0 / sales_30d.units_sold_30d) END) ASC, p.sku ASC";
		} else {
			orderClause = "ORDER BY (CASE WHEN bi.current_stock <= 0 THEN 0 ELSE 1 END) ASC, p.sku ASC";
		}

		String countSql = """
				SELECT COUNT(*)
				FROM branch_inventories bi
				JOIN branches b ON b.id = bi.branch_id
				JOIN products p ON p.id = bi.product_id
				WHERE b.external_id = :branchExternalId
				  AND p.is_active = TRUE
				  AND bi.current_stock <= bi.min_stock_threshold
				""" + severityCondition;

		Long total = jdbcClient.sql(countSql)
				.param("branchExternalId", branchExternalId)
				.query(Long.class)
				.single();
		long totalElements = total != null ? total : 0L;

		int limit = Math.max(size, 1);
		long offset = (long) Math.max(page, 0) * limit;

		String querySql = """
				SELECT
				    p.external_id AS product_external_id,
				    p.sku AS sku,
				    p.name AS name,
				    bi.current_stock AS current_stock,
				    bi.min_stock_threshold AS min_stock_threshold,
				    COALESCE(sales_30d.units_sold_30d, 0) AS units_sold_30d
				FROM branch_inventories bi
				JOIN branches b ON b.id = bi.branch_id
				JOIN products p ON p.id = bi.product_id
				LEFT JOIN (
				    SELECT si.product_id, SUM(si.quantity) AS units_sold_30d
				    FROM sales s
				    JOIN sale_items si ON si.sale_id = s.id
				    JOIN branches b2 ON b2.id = s.branch_id
				    WHERE b2.external_id = :branchExternalId
				      AND s.status = 'COMPLETED'
				      AND s.created_at >= NOW() - INTERVAL '30 days'
				    GROUP BY si.product_id
				) sales_30d ON sales_30d.product_id = p.id
				WHERE b.external_id = :branchExternalId
				  AND p.is_active = TRUE
				  AND bi.current_stock <= bi.min_stock_threshold
				""" + severityCondition + " " + orderClause + """
				 LIMIT :limit OFFSET :offset
				""";

		List<ReplenishmentLine> content = jdbcClient.sql(querySql)
				.param("branchExternalId", branchExternalId)
				.param("limit", limit)
				.param("offset", offset)
				.query((rs, rowNum) -> {
					UUID productExternalId = rs.getObject("product_external_id", UUID.class);
					String sku = rs.getString("sku");
					String name = rs.getString("name");
					BigDecimal currentStock = rs.getBigDecimal("current_stock");
					BigDecimal minStockThreshold = rs.getBigDecimal("min_stock_threshold");
					BigDecimal unitsSold30d = rs.getBigDecimal("units_sold_30d");

					ReplenishmentSeverity lineSeverity = ReplenishmentSeverity.of(currentStock);
					BigDecimal coverageDays = CoveragePolicy.calculateCoverageDays(currentStock, unitsSold30d, 30);
					return new ReplenishmentLine(productExternalId, sku, name, currentStock, minStockThreshold,
							lineSeverity, coverageDays);
				})
				.list();

		return new AnalyticsPage<>(content, totalElements, Math.max(page, 0), limit);
	}
}
