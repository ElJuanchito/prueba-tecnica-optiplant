package com.optiplant.inventory.analytics.infrastructure.adapter.in.web;

import com.optiplant.inventory.analytics.application.port.in.QueryCorporateBoardUseCase;
import com.optiplant.inventory.analytics.application.port.in.QueryCorporateBoardUseCase.CorporateBoardQuery;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the corporate comparative board across branches (CU-DSH-03, RF-DSH-05, contract §6).
 * Restricted to {@code ADMIN} via SecurityConfig matcher (R-19, design §7).
 */
@RestController
@RequestMapping("/api/analytics/corporate/branches")
public class CorporateBoardController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final QueryCorporateBoardUseCase queryCorporateBoardUseCase;

	public CorporateBoardController(QueryCorporateBoardUseCase queryCorporateBoardUseCase) {
		this.queryCorporateBoardUseCase = queryCorporateBoardUseCase;
	}

	@GetMapping
	public PageResponse<BranchPerformanceResponse> corporateBoard(
			@RequestParam(required = false) Integer year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) String sort,
			@RequestParam(required = false) String direction,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		if (month != null && (month < 1 || month > 12)) {
			throw new IllegalArgumentException("month must be between 1 and 12");
		}

		int resolvedSize = resolveSize(size);
		int resolvedPage = Math.max(page, 0);

		AnalyticsPage<BranchPerformance> result = queryCorporateBoardUseCase.corporateBoard(
				new CorporateBoardQuery(year, month, sort, direction, resolvedPage, resolvedSize));

		List<BranchPerformanceResponse> content = result.content().stream()
				.map(b -> new BranchPerformanceResponse(b.branchExternalId(), b.code(), b.name(),
						b.salesAmount(), b.salesCount(), b.unitsSold(), b.inventoryValue(),
						b.criticalProductCount(), b.activeTransferCount()))
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

	public record BranchPerformanceResponse(UUID branchExternalId, String code, String name,
			BigDecimal salesAmount, long salesCount, BigDecimal unitsSold, BigDecimal inventoryValue,
			long criticalProductCount, long activeTransferCount) {
	}

	public record PageResponse<T>(List<T> content, long totalElements, int page, int size) {
	}
}
