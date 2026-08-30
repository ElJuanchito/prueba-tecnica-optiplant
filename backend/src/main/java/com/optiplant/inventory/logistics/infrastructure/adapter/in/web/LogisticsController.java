package com.optiplant.inventory.logistics.infrastructure.adapter.in.web;

import com.optiplant.inventory.logistics.application.port.in.ManageRoutesUseCase;
import com.optiplant.inventory.logistics.application.port.in.ManageRoutesUseCase.CreateRouteCommand;
import com.optiplant.inventory.logistics.application.port.in.ManageRoutesUseCase.RouteListQuery;
import com.optiplant.inventory.logistics.application.port.in.ManageRoutesUseCase.UpdateRouteCommand;
import com.optiplant.inventory.logistics.application.port.in.MonitorTransfersUseCase;
import com.optiplant.inventory.logistics.application.port.in.MonitorTransfersUseCase.ActiveTransferQuery;
import com.optiplant.inventory.logistics.application.port.in.ReportComplianceUseCase;
import com.optiplant.inventory.logistics.application.port.in.ReportComplianceUseCase.ComplianceQuery;
import com.optiplant.inventory.logistics.domain.model.ActiveTransferPage;
import com.optiplant.inventory.logistics.domain.model.ActiveTransferView;
import com.optiplant.inventory.logistics.domain.model.BranchReference;
import com.optiplant.inventory.logistics.domain.model.ComplianceGrouping;
import com.optiplant.inventory.logistics.domain.model.CompliancePage;
import com.optiplant.inventory.logistics.domain.model.ComplianceRow;
import com.optiplant.inventory.logistics.domain.model.RoutePage;
import com.optiplant.inventory.logistics.domain.model.RoutePriority;
import com.optiplant.inventory.logistics.domain.model.RouteSummary;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/logistics/**} — the six endpoints of contract §6: route CRUD (CU-LOG-01),
 * the active-transfer monitor (CU-LOG-02) and the compliance report (CU-LOG-03). No endpoint
 * accepts a branch identifier in path, query or body (RN-14). Page size is rejected above
 * {@value #MAX_PAGE_SIZE}, never clamped (R-00).
 */
@RestController
@RequestMapping("/api/logistics")
public class LogisticsController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageRoutesUseCase manageRoutesUseCase;
	private final MonitorTransfersUseCase monitorTransfersUseCase;
	private final ReportComplianceUseCase reportComplianceUseCase;
	private final PrincipalAccessor principalAccessor;

	public LogisticsController(ManageRoutesUseCase manageRoutesUseCase, MonitorTransfersUseCase monitorTransfersUseCase,
			ReportComplianceUseCase reportComplianceUseCase, PrincipalAccessor principalAccessor) {
		this.manageRoutesUseCase = manageRoutesUseCase;
		this.monitorTransfersUseCase = monitorTransfersUseCase;
		this.reportComplianceUseCase = reportComplianceUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping("/routes")
	public ResponseEntity<RouteResponse> createRoute(@Valid @RequestBody CreateRouteRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		RouteSummary created = manageRoutesUseCase.create(actor,
				new CreateRouteCommand(request.originBranchExternalId(), request.destinationBranchExternalId(),
						request.estimatedDurationHours(), request.transportCost(), request.priorityLevel()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toRouteResponse(created));
	}

	@GetMapping("/routes")
	public RoutePageResponse listRoutes(@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		RoutePage result = manageRoutesUseCase.list(actor, new RouteListQuery(active, Math.max(page, 0), resolveSize(size)));
		List<RouteResponse> content = result.content().stream().map(LogisticsController::toRouteResponse).toList();
		return new RoutePageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@PutMapping("/routes/{externalId}")
	public RouteResponse updateRoute(@PathVariable UUID externalId, @Valid @RequestBody UpdateRouteRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		RouteSummary updated = manageRoutesUseCase.update(actor, externalId, new UpdateRouteCommand(
				request.estimatedDurationHours(), request.transportCost(), request.priorityLevel()));
		return toRouteResponse(updated);
	}

	@PatchMapping("/routes/{externalId}/deactivation")
	public RouteResponse deactivateRoute(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toRouteResponse(manageRoutesUseCase.deactivate(actor, externalId));
	}

	@GetMapping("/transfers/active")
	public ActiveTransferPageResponse listActiveTransfers(@RequestParam(required = false) String status,
			@RequestParam(required = false) Boolean delayed, @RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		ActiveTransferPage result = monitorTransfersUseCase.listActive(actor,
				new ActiveTransferQuery(status, delayed, Math.max(page, 0), resolveSize(size)));
		List<ActiveTransferResponse> content = result.content().stream().map(LogisticsController::toActiveResponse)
				.toList();
		return new ActiveTransferPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/compliance")
	public CompliancePageResponse compliance(@RequestParam @NotNull Instant from, @RequestParam @NotNull Instant to,
			@RequestParam(defaultValue = "ROUTE") ComplianceGrouping groupBy, @RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		CompliancePage result = reportComplianceUseCase.report(actor,
				new ComplianceQuery(from, to, groupBy, Math.max(page, 0), resolveSize(size)));
		List<ComplianceRowResponse> content = result.content().stream().map(LogisticsController::toComplianceResponse)
				.toList();
		return new CompliancePageResponse(content, result.totalElements(), result.page(), result.size());
	}

	/** R-00: a size above {@link #MAX_PAGE_SIZE} is refused, never silently clamped (RNF-PER-04). */
	private static int resolveSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return size;
	}

	private static RouteResponse toRouteResponse(RouteSummary route) {
		return new RouteResponse(route.externalId(), toBranchResponse(route.originBranch()),
				toBranchResponse(route.destinationBranch()), route.estimatedDurationHours(), route.transportCost(),
				route.priorityLevel(), route.active(), route.createdAt());
	}

	private static ActiveTransferResponse toActiveResponse(ActiveTransferView view) {
		return new ActiveTransferResponse(view.transferExternalId(), view.transferNumber(), view.status(),
				toBranchResponse(view.originBranch()), toBranchResponse(view.destinationBranch()), view.priority(),
				view.itemCount(), view.totalQuantity(), view.estimatedArrivalAt(), view.isDelayed());
	}

	private static ComplianceRowResponse toComplianceResponse(ComplianceRow row) {
		return new ComplianceRowResponse(row.key(), row.label(), row.deliveredCount(), row.onTimeCount(),
				row.onTimePercentage(), row.averageDeviationHours(), row.unmeasuredCount());
	}

	private static BranchReferenceResponse toBranchResponse(BranchReference branch) {
		return branch == null ? null : new BranchReferenceResponse(branch.externalId(), branch.name());
	}

	public record CreateRouteRequest(@NotNull UUID originBranchExternalId, @NotNull UUID destinationBranchExternalId,
			@NotNull BigDecimal estimatedDurationHours, @NotNull BigDecimal transportCost,
			@NotNull RoutePriority priorityLevel) {
	}

	public record UpdateRouteRequest(@NotNull BigDecimal estimatedDurationHours, @NotNull BigDecimal transportCost,
			@NotNull RoutePriority priorityLevel) {
	}

	public record BranchReferenceResponse(UUID externalId, String name) {
	}

	public record RouteResponse(UUID externalId, BranchReferenceResponse originBranch,
			BranchReferenceResponse destinationBranch, BigDecimal estimatedDurationHours, BigDecimal transportCost,
			RoutePriority priorityLevel, boolean active, Instant createdAt) {
	}

	public record RoutePageResponse(List<RouteResponse> content, long totalElements, int page, int size) {
	}

	public record ActiveTransferResponse(UUID transferExternalId, String transferNumber, String status,
			BranchReferenceResponse originBranch, BranchReferenceResponse destinationBranch, String priority,
			long itemCount, BigDecimal totalQuantity, Instant estimatedArrivalAt, boolean isDelayed) {
	}

	public record ActiveTransferPageResponse(List<ActiveTransferResponse> content, long totalElements, int page,
			int size) {
	}

	public record ComplianceRowResponse(String key, String label, long deliveredCount, long onTimeCount,
			BigDecimal onTimePercentage, BigDecimal averageDeviationHours, long unmeasuredCount) {
	}

	public record CompliancePageResponse(List<ComplianceRowResponse> content, long totalElements, int page, int size) {
	}
}
