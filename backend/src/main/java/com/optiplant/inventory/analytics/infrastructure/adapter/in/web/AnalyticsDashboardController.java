package com.optiplant.inventory.analytics.infrastructure.adapter.in.web;

import com.optiplant.inventory.analytics.application.port.in.QueryProductRotationUseCase;
import com.optiplant.inventory.analytics.application.port.in.QueryProductRotationUseCase.RotationQuery;
import com.optiplant.inventory.analytics.application.port.in.QueryReplenishmentUseCase;
import com.optiplant.inventory.analytics.application.port.in.QueryReplenishmentUseCase.ReplenishmentQuery;
import com.optiplant.inventory.analytics.application.port.in.QuerySalesTrendUseCase;
import com.optiplant.inventory.analytics.application.port.in.QuerySalesTrendUseCase.SalesTrendQuery;
import com.optiplant.inventory.analytics.application.port.in.QueryTransferActivityUseCase;
import com.optiplant.inventory.analytics.application.port.in.QueryTransferActivityUseCase.StockImpactQuery;
import com.optiplant.inventory.analytics.domain.model.AbcClass;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentLine;
import com.optiplant.inventory.analytics.domain.model.ReplenishmentSeverity;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.model.RotationLine;
import com.optiplant.inventory.analytics.domain.model.SalesTrend;
import com.optiplant.inventory.analytics.domain.model.TransferActivitySummary;
import com.optiplant.inventory.analytics.domain.model.TransferStatusCounts;
import com.optiplant.inventory.analytics.domain.model.TransferStockImpact;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for branch operational dashboards and replenishment (contract §6, design §7).
 * All endpoints are read-only {@code GET} mappings (R-01) exposing only {@code external_id} UUIDs (RNF-API-02).
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsDashboardController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final QuerySalesTrendUseCase querySalesTrendUseCase;
	private final QueryProductRotationUseCase queryProductRotationUseCase;
	private final QueryTransferActivityUseCase queryTransferActivityUseCase;
	private final QueryReplenishmentUseCase queryReplenishmentUseCase;
	private final PrincipalAccessor principalAccessor;

	public AnalyticsDashboardController(QuerySalesTrendUseCase querySalesTrendUseCase,
			QueryProductRotationUseCase queryProductRotationUseCase,
			QueryTransferActivityUseCase queryTransferActivityUseCase,
			QueryReplenishmentUseCase queryReplenishmentUseCase,
			PrincipalAccessor principalAccessor) {
		this.querySalesTrendUseCase = querySalesTrendUseCase;
		this.queryProductRotationUseCase = queryProductRotationUseCase;
		this.queryTransferActivityUseCase = queryTransferActivityUseCase;
		this.queryReplenishmentUseCase = queryReplenishmentUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping("/dashboard/sales-trend")
	public SalesTrendResponse salesTrend(
			@RequestParam(required = false, defaultValue = "4") int months,
			@RequestParam(required = false) UUID branchExternalId) {
		if (months < 1 || months > 12) {
			throw new IllegalArgumentException("months must be between 1 and 12");
		}
		AuthenticatedPrincipal actor = principalAccessor.require();
		SalesTrend trend = querySalesTrendUseCase.salesTrend(actor, new SalesTrendQuery(months, branchExternalId));

		List<MonthlySalesResponse> monthResponses = trend.months().stream()
				.map(m -> new MonthlySalesResponse(m.year(), m.month(), m.salesCount(), m.unitsSold(), m.totalAmount()))
				.toList();

		return new SalesTrendResponse(trend.branchExternalId(), monthResponses,
				trend.monthOverMonthVariationPercent(), trend.empty());
	}

	@GetMapping("/dashboard/rotation")
	public PageResponse<RotationLineResponse> rotation(
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(required = false, defaultValue = "TOP") String direction,
			@RequestParam(required = false) UUID branchExternalId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		RotationDirection dir;
		try {
			dir = RotationDirection.valueOf(direction.toUpperCase());
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid direction: " + direction);
		}

		int resolvedSize = resolveSize(size);
		int resolvedPage = Math.max(page, 0);

		AuthenticatedPrincipal actor = principalAccessor.require();
		AnalyticsPage<RotationLine> result = queryProductRotationUseCase.rotation(actor,
				new RotationQuery(from, to, dir, branchExternalId, resolvedPage, resolvedSize));

		List<RotationLineResponse> content = result.content().stream()
				.map(r -> new RotationLineResponse(r.productExternalId(), r.sku(), r.name(),
						r.unitsSold(), r.salesAmount(), r.sharePercent(), r.cumulativeSharePercent(),
						r.abcClass(), r.coverageDays()))
				.toList();

		return new PageResponse<>(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/dashboard/transfers")
	public TransferActivitySummaryResponse transferSummary(@RequestParam(required = false) UUID branchExternalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		TransferActivitySummary summary = queryTransferActivityUseCase.summary(actor, branchExternalId);
		return new TransferActivitySummaryResponse(
				new TransferStatusCountsResponse(summary.inbound().requested(), summary.inbound().inPreparation(), summary.inbound().inTransit()),
				new TransferStatusCountsResponse(summary.outbound().requested(), summary.outbound().inPreparation(), summary.outbound().inTransit()),
				summary.delayedCount()
		);
	}

	@GetMapping("/dashboard/transfers/stock-impact")
	public PageResponse<TransferStockImpactResponse> stockImpact(
			@RequestParam(required = false) UUID branchExternalId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		int resolvedSize = resolveSize(size);
		int resolvedPage = Math.max(page, 0);

		AuthenticatedPrincipal actor = principalAccessor.require();
		AnalyticsPage<TransferStockImpact> result = queryTransferActivityUseCase.stockImpact(actor,
				new StockImpactQuery(branchExternalId, resolvedPage, resolvedSize));

		List<TransferStockImpactResponse> content = result.content().stream()
				.map(t -> new TransferStockImpactResponse(t.productExternalId(), t.sku(), t.name(),
						t.currentStock(), t.inTransitStock(), t.inboundInTransit(), t.outboundCommitted(),
						t.projectedStock()))
				.toList();

		return new PageResponse<>(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/replenishment")
	public PageResponse<ReplenishmentLineResponse> replenishment(
			@RequestParam(required = false) String severity,
			@RequestParam(required = false, defaultValue = "severity") String sort,
			@RequestParam(required = false) UUID branchExternalId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		ReplenishmentSeverity sev = null;
		if (severity != null && !severity.isBlank()) {
			try {
				sev = ReplenishmentSeverity.valueOf(severity.toUpperCase());
			} catch (Exception ex) {
				throw new IllegalArgumentException("Invalid severity: " + severity);
			}
		}

		if (sort != null && !sort.isBlank() && !List.of("severity", "product", "coverage").contains(sort.toLowerCase())) {
			throw new IllegalArgumentException("Invalid sort key: " + sort);
		}

		int resolvedSize = resolveSize(size);
		int resolvedPage = Math.max(page, 0);

		AuthenticatedPrincipal actor = principalAccessor.require();
		AnalyticsPage<ReplenishmentLine> result = queryReplenishmentUseCase.replenishment(actor,
				new ReplenishmentQuery(sev, sort, branchExternalId, resolvedPage, resolvedSize));

		List<ReplenishmentLineResponse> content = result.content().stream()
				.map(r -> new ReplenishmentLineResponse(r.productExternalId(), r.sku(), r.name(),
						r.currentStock(), r.minStockThreshold(), r.severity(), r.coverageDays()))
				.toList();

		return new PageResponse<>(content, result.totalElements(), result.page(), result.size());
	}

	private static int resolveSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return size;
	}

	public record MonthlySalesResponse(int year, int month, long salesCount, BigDecimal unitsSold, BigDecimal totalAmount) {
	}

	public record SalesTrendResponse(UUID branchExternalId, List<MonthlySalesResponse> months,
			BigDecimal monthOverMonthVariationPercent, boolean empty) {
	}

	public record RotationLineResponse(UUID productExternalId, String sku, String name, BigDecimal unitsSold,
			BigDecimal salesAmount, BigDecimal sharePercent, BigDecimal cumulativeSharePercent,
			AbcClass abcClass, BigDecimal coverageDays) {
	}

	public record TransferStatusCountsResponse(long requested, long inPreparation, long inTransit) {
	}

	public record TransferActivitySummaryResponse(TransferStatusCountsResponse inbound,
			TransferStatusCountsResponse outbound, long delayedCount) {
	}

	public record TransferStockImpactResponse(UUID productExternalId, String sku, String name,
			BigDecimal currentStock, BigDecimal inTransitStock, BigDecimal inboundInTransit,
			BigDecimal outboundCommitted, BigDecimal projectedStock) {
	}

	public record ReplenishmentLineResponse(UUID productExternalId, String sku, String name,
			BigDecimal currentStock, BigDecimal minStockThreshold, ReplenishmentSeverity severity,
			BigDecimal coverageDays) {
	}

	public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {
	}
}
