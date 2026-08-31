package com.optiplant.inventory.analytics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.analytics.application.port.out.TransferAnalyticsPort;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStatusCounts;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL read adapter for active transfers and stock impact (design §4 Q-5, Q-6).
 * Reads {@code transfers}, {@code transfer_items}, {@code products}, and {@code branch_inventories}
 * via {@link JdbcClient} without JPA entities (P-01). Reports {@code in_transit_stock} as stored (R-14).
 */
@Component
public class TransferAnalyticsJdbcAdapter implements TransferAnalyticsPort {

	private final JdbcClient jdbcClient;

	public TransferAnalyticsJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public TransferActivitySummary activitySummary(UUID branchExternalId) {
		String sql = """
				SELECT
				    COUNT(CASE WHEN t.destination_branch_id = b.id AND t.status = 'REQUESTED' THEN 1 END) AS in_requested,
				    COUNT(CASE WHEN t.destination_branch_id = b.id AND t.status = 'IN_PREPARATION' THEN 1 END) AS in_prep,
				    COUNT(CASE WHEN t.destination_branch_id = b.id AND t.status = 'IN_TRANSIT' THEN 1 END) AS in_transit,
				    COUNT(CASE WHEN t.origin_branch_id = b.id AND t.status = 'REQUESTED' THEN 1 END) AS out_requested,
				    COUNT(CASE WHEN t.origin_branch_id = b.id AND t.status = 'IN_PREPARATION' THEN 1 END) AS out_prep,
				    COUNT(CASE WHEN t.origin_branch_id = b.id AND t.status = 'IN_TRANSIT' THEN 1 END) AS out_transit,
				    COUNT(CASE WHEN (t.destination_branch_id = b.id OR t.origin_branch_id = b.id)
				                    AND t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
				                    AND t.estimated_arrival_at < NOW()
				                    AND t.actual_arrival_at IS NULL THEN 1 END) AS delayed_count
				FROM branches b
				LEFT JOIN transfers t ON ((t.destination_branch_id = b.id OR t.origin_branch_id = b.id)
				                          AND t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT'))
				WHERE b.external_id = :branchExternalId
				""";

		return jdbcClient.sql(sql)
				.param("branchExternalId", branchExternalId)
				.query((rs, rowNum) -> {
					TransferStatusCounts inbound = new TransferStatusCounts(
							rs.getLong("in_requested"),
							rs.getLong("in_prep"),
							rs.getLong("in_transit")
					);
					TransferStatusCounts outbound = new TransferStatusCounts(
							rs.getLong("out_requested"),
							rs.getLong("out_prep"),
							rs.getLong("out_transit")
					);
					long delayed = rs.getLong("delayed_count");
					return new TransferActivitySummary(inbound, outbound, delayed);
				})
				.single();
	}

	@Override
	public AnalyticsPage<TransferStockImpact> stockImpact(UUID branchExternalId, int page, int size) {
		String countSql = """
				SELECT COUNT(DISTINCT ti.product_id)
				FROM transfers t
				JOIN branches b ON b.external_id = :branchExternalId
				JOIN transfer_items ti ON ti.transfer_id = t.id
				JOIN products p ON p.id = ti.product_id
				WHERE (t.destination_branch_id = b.id OR t.origin_branch_id = b.id)
				  AND t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
				  AND p.is_active = TRUE
				""";

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
				    COALESCE(bi.current_stock, 0) AS current_stock,
				    COALESCE(bi.in_transit_stock, 0) AS in_transit_stock,
				    COALESCE(SUM(CASE WHEN t.destination_branch_id = b.id AND t.status = 'IN_TRANSIT' THEN ti.dispatched_quantity ELSE 0 END), 0) AS inbound_in_transit,
				    COALESCE(SUM(CASE WHEN t.origin_branch_id = b.id AND t.status IN ('REQUESTED', 'IN_PREPARATION') THEN ti.requested_quantity ELSE 0 END), 0) AS outbound_committed
				FROM transfers t
				JOIN branches b ON b.external_id = :branchExternalId
				JOIN transfer_items ti ON ti.transfer_id = t.id
				JOIN products p ON p.id = ti.product_id
				LEFT JOIN branch_inventories bi ON (bi.branch_id = b.id AND bi.product_id = p.id)
				WHERE (t.destination_branch_id = b.id OR t.origin_branch_id = b.id)
				  AND t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
				  AND p.is_active = TRUE
				GROUP BY p.id, p.external_id, p.sku, p.name, bi.current_stock, bi.in_transit_stock
				ORDER BY p.sku ASC
				LIMIT :limit OFFSET :offset
				""";

		List<TransferStockImpact> content = jdbcClient.sql(querySql)
				.param("branchExternalId", branchExternalId)
				.param("limit", limit)
				.param("offset", offset)
				.query((rs, rowNum) -> {
					UUID productExternalId = rs.getObject("product_external_id", UUID.class);
					String sku = rs.getString("sku");
					String name = rs.getString("name");
					BigDecimal currentStock = rs.getBigDecimal("current_stock");
					BigDecimal inTransitStock = rs.getBigDecimal("in_transit_stock");
					BigDecimal inboundInTransit = rs.getBigDecimal("inbound_in_transit");
					BigDecimal outboundCommitted = rs.getBigDecimal("outbound_committed");
					BigDecimal projectedStock = currentStock.add(inboundInTransit).subtract(outboundCommitted);

					return new TransferStockImpact(productExternalId, sku, name, currentStock, inTransitStock,
							inboundInTransit, outboundCommitted, projectedStock);
				})
				.list();

		return new AnalyticsPage<>(content, totalElements, Math.max(page, 0), limit);
	}
}
