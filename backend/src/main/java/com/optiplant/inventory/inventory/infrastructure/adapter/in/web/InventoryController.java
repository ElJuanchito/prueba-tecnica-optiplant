package com.optiplant.inventory.inventory.infrastructure.adapter.in.web;

import com.optiplant.inventory.inventory.application.port.in.ManageStockThresholdUseCase;
import com.optiplant.inventory.inventory.application.port.in.QueryKardexUseCase;
import com.optiplant.inventory.inventory.application.port.in.QueryKardexUseCase.KardexQuery;
import com.optiplant.inventory.inventory.application.port.in.QueryStockUseCase;
import com.optiplant.inventory.inventory.application.port.in.QueryStockUseCase.StockQuery;
import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase;
import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase.AdjustStockCommand;
import com.optiplant.inventory.inventory.application.port.in.RegisterStockMovementUseCase.WriteOffCommand;
import com.optiplant.inventory.inventory.domain.model.BranchAvailability;
import com.optiplant.inventory.inventory.domain.model.KardexLine;
import com.optiplant.inventory.inventory.domain.model.KardexPage;
import com.optiplant.inventory.inventory.domain.model.MovementReceipt;
import com.optiplant.inventory.inventory.domain.model.NetworkAvailability;
import com.optiplant.inventory.inventory.domain.model.StockLine;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import com.optiplant.inventory.inventory.domain.model.ThresholdView;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import com.optiplant.inventory.shared.stock.StockMovementType;
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
 * {@code /api/inventory/**} — the six endpoints of contract §6. No endpoint accepts
 * {@code branchId} in path, query or body (RN-14): the branch is always
 * {@code principalAccessor.require()}'s session-derived branch, resolved further down inside
 * the application services. Page size is clamped server-side to {@value #MAX_PAGE_SIZE} — a
 * size above the cap is rejected ({@code invalid_request}), not silently clamped (R-00,
 * RNF-PER-04).
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final QueryStockUseCase queryStockUseCase;
	private final RegisterStockMovementUseCase registerStockMovementUseCase;
	private final ManageStockThresholdUseCase manageStockThresholdUseCase;
	private final QueryKardexUseCase queryKardexUseCase;
	private final PrincipalAccessor principalAccessor;

	public InventoryController(QueryStockUseCase queryStockUseCase,
			RegisterStockMovementUseCase registerStockMovementUseCase,
			ManageStockThresholdUseCase manageStockThresholdUseCase, QueryKardexUseCase queryKardexUseCase,
			PrincipalAccessor principalAccessor) {
		this.queryStockUseCase = queryStockUseCase;
		this.registerStockMovementUseCase = registerStockMovementUseCase;
		this.manageStockThresholdUseCase = manageStockThresholdUseCase;
		this.queryKardexUseCase = queryKardexUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping("/stock")
	public StockPageResponse listStock(@RequestParam(required = false) UUID productExternalId,
			@RequestParam(required = false, defaultValue = "false") boolean belowThreshold,
			@RequestParam(required = false, defaultValue = "product") String sort,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		StockPage result = queryStockUseCase.listOwnBranchStock(actor,
				new StockQuery(productExternalId, belowThreshold, sort, Math.max(page, 0), resolveSize(size)));
		List<StockLineResponse> content = result.content().stream().map(InventoryController::toStockLineResponse)
				.toList();
		return new StockPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/stock/{productExternalId}/network")
	public NetworkAvailabilityResponse networkAvailability(@PathVariable UUID productExternalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toNetworkResponse(queryStockUseCase.networkAvailability(actor, productExternalId));
	}

	@PostMapping("/adjustments")
	public ResponseEntity<MovementReceiptResponse> adjust(@Valid @RequestBody AdjustStockRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		MovementReceipt receipt = registerStockMovementUseCase.adjust(actor,
				new AdjustStockCommand(request.productExternalId(), request.countedQuantity(), request.reason()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toReceiptResponse(receipt));
	}

	@PostMapping("/write-offs")
	public ResponseEntity<MovementReceiptResponse> writeOff(@Valid @RequestBody WriteOffRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		MovementReceipt receipt = registerStockMovementUseCase.writeOff(actor,
				new WriteOffCommand(request.productExternalId(), request.quantity(), request.reason()));
		return ResponseEntity.status(HttpStatus.CREATED).body(toReceiptResponse(receipt));
	}

	@PutMapping("/stock/{productExternalId}/threshold")
	public ThresholdResponse setThreshold(@PathVariable UUID productExternalId,
			@Valid @RequestBody SetThresholdRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		ThresholdView view = manageStockThresholdUseCase.setThreshold(actor, productExternalId,
				request.minStockThreshold());
		return new ThresholdResponse(view.productExternalId(), view.minStockThreshold());
	}

	@GetMapping("/kardex")
	public KardexPageResponse listKardex(@RequestParam(required = false) UUID productExternalId,
			@RequestParam(required = false) StockMovementType movementType,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		KardexPage result = queryKardexUseCase.list(actor, new KardexQuery(productExternalId, movementType, from, to,
				Math.max(page, 0), resolveSize(size)));
		List<KardexLineResponse> content = result.content().stream().map(InventoryController::toKardexLineResponse)
				.toList();
		return new KardexPageResponse(content, result.totalElements(), result.page(), result.size());
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

	private static StockLineResponse toStockLineResponse(StockLine line) {
		return new StockLineResponse(line.productExternalId(), line.sku(), line.name(), line.currentStock(),
				line.reservedStock(), line.inTransitStock(), line.availableStock(), line.minStockThreshold(),
				line.averageCost(), line.lastUpdatedAt());
	}

	private static NetworkAvailabilityResponse toNetworkResponse(NetworkAvailability availability) {
		List<BranchAvailabilityResponse> branches = availability.branches().stream()
				.map(InventoryController::toBranchAvailabilityResponse).toList();
		return new NetworkAvailabilityResponse(availability.productExternalId(), availability.sku(),
				availability.name(), branches, availability.networkTotal());
	}

	private static BranchAvailabilityResponse toBranchAvailabilityResponse(BranchAvailability branch) {
		return new BranchAvailabilityResponse(branch.branchExternalId(), branch.branchName(), branch.currentStock(),
				branch.reservedStock(), branch.inTransitStock(), branch.availableStock(), branch.isOwnBranch());
	}

	private static MovementReceiptResponse toReceiptResponse(MovementReceipt receipt) {
		return new MovementReceiptResponse(receipt.movementExternalId(), receipt.movementType(), receipt.quantity(),
				receipt.previousStock(), receipt.resultingStock(), receipt.createdAt());
	}

	private static KardexLineResponse toKardexLineResponse(KardexLine line) {
		return new KardexLineResponse(line.externalId(), line.productExternalId(), line.movementType(),
				line.quantity(), line.unitCost(), line.totalCost(), line.previousStock(), line.resultingStock(),
				line.referenceType(), line.referenceId(), line.notes(), line.userExternalId(), line.createdAt());
	}

	// Bean validation covers only structural nulls; "reason" is deliberately unconstrained here
	// so a blank/absent value reaches MovementReason and raises AdjustmentReasonRequiredException
	// (R-07), not the generic invalid_request a @NotBlank would produce instead.
	public record AdjustStockRequest(@NotNull UUID productExternalId, @NotNull BigDecimal countedQuantity,
			String reason) {
	}

	public record WriteOffRequest(@NotNull UUID productExternalId, @NotNull BigDecimal quantity, String reason) {
	}

	public record SetThresholdRequest(@NotNull BigDecimal minStockThreshold) {
	}

	public record StockLineResponse(UUID productExternalId, String sku, String name, BigDecimal currentStock,
			BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock,
			BigDecimal minStockThreshold, BigDecimal averageCost, Instant lastUpdatedAt) {
	}

	public record StockPageResponse(List<StockLineResponse> content, long totalElements, int page, int size) {
	}

	public record BranchAvailabilityResponse(UUID branchExternalId, String branchName, BigDecimal currentStock,
			BigDecimal reservedStock, BigDecimal inTransitStock, BigDecimal availableStock, Boolean isOwnBranch) {
	}

	public record NetworkAvailabilityResponse(UUID productExternalId, String sku, String name,
			List<BranchAvailabilityResponse> branches, BigDecimal networkTotal) {
	}

	public record MovementReceiptResponse(UUID movementExternalId, StockMovementType movementType,
			BigDecimal quantity, BigDecimal previousStock, BigDecimal resultingStock, Instant createdAt) {
	}

	public record ThresholdResponse(UUID productExternalId, BigDecimal minStockThreshold) {
	}

	public record KardexLineResponse(UUID externalId, UUID productExternalId, StockMovementType movementType,
			BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, BigDecimal previousStock,
			BigDecimal resultingStock, String referenceType, String referenceId, String notes, UUID userExternalId,
			Instant createdAt) {
	}

	public record KardexPageResponse(List<KardexLineResponse> content, long totalElements, int page, int size) {
	}
}
