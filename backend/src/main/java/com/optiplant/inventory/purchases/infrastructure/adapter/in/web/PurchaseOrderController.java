package com.optiplant.inventory.purchases.infrastructure.adapter.in.web;

import com.optiplant.inventory.purchases.application.port.in.ManagePurchaseOrdersUseCase;
import com.optiplant.inventory.purchases.application.port.in.ManagePurchaseOrdersUseCase.CreatePurchaseOrderCommand;
import com.optiplant.inventory.purchases.application.port.in.ManagePurchaseOrdersUseCase.EditPurchaseOrderCommand;
import com.optiplant.inventory.purchases.application.port.in.ManagePurchaseOrdersUseCase.PurchaseOrderLineCommand;
import com.optiplant.inventory.purchases.application.port.in.QueryPurchasesUseCase;
import com.optiplant.inventory.purchases.application.port.in.QueryPurchasesUseCase.CostHistoryQuery;
import com.optiplant.inventory.purchases.application.port.in.QueryPurchasesUseCase.PurchaseOrderListQuery;
import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase;
import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase.ReceivePurchaseCommand;
import com.optiplant.inventory.purchases.application.port.in.ReceivePurchaseUseCase.ReceptionItemCommand;
import com.optiplant.inventory.purchases.application.port.in.TransitionPurchaseOrderUseCase;
import com.optiplant.inventory.purchases.application.port.in.TransitionPurchaseOrderUseCase.CancelPurchaseOrderCommand;
import com.optiplant.inventory.purchases.domain.model.BranchRef;
import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItemView;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.SupplierRef;
import com.optiplant.inventory.purchases.domain.model.UserRef;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for purchase orders and cost history (CU-COM-02..05, contract §6).
 * No endpoint accepts a branch identifier (RN-14); oversized pages are rejected (R-00).
 */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseOrderController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManagePurchaseOrdersUseCase managePurchaseOrdersUseCase;
	private final TransitionPurchaseOrderUseCase transitionPurchaseOrderUseCase;
	private final ReceivePurchaseUseCase receivePurchaseUseCase;
	private final QueryPurchasesUseCase queryPurchasesUseCase;
	private final PrincipalAccessor principalAccessor;

	public PurchaseOrderController(ManagePurchaseOrdersUseCase managePurchaseOrdersUseCase,
			TransitionPurchaseOrderUseCase transitionPurchaseOrderUseCase,
			ReceivePurchaseUseCase receivePurchaseUseCase,
			QueryPurchasesUseCase queryPurchasesUseCase,
			PrincipalAccessor principalAccessor) {
		this.managePurchaseOrdersUseCase = managePurchaseOrdersUseCase;
		this.transitionPurchaseOrderUseCase = transitionPurchaseOrderUseCase;
		this.receivePurchaseUseCase = receivePurchaseUseCase;
		this.queryPurchasesUseCase = queryPurchasesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping("/orders")
	public ResponseEntity<PurchaseOrderDetailResponse> create(@Valid @RequestBody CreatePurchaseOrderRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<PurchaseOrderLineCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new PurchaseOrderLineCommand(
						item.productExternalId(),
						item.quantity(),
						item.unitOfMeasureExternalId(),
						item.unitCost(),
						item.discountPercent()
				))
				.toList();

		CreatePurchaseOrderCommand command = new CreatePurchaseOrderCommand(
				request.supplierExternalId(),
				request.paymentTerms(),
				request.notes(),
				items
		);

		PurchaseOrderDetail detail = managePurchaseOrdersUseCase.create(actor, command);
		return ResponseEntity.status(HttpStatus.CREATED).body(toDetailResponse(detail));
	}

	@GetMapping("/orders")
	public PurchaseOrderPageResponse list(
			@RequestParam(required = false) UUID supplierExternalId,
			@RequestParam(required = false) UUID productExternalId,
			@RequestParam(required = false) PurchaseOrderStatus status,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		PurchasePage<PurchaseOrderSummary> result = queryPurchasesUseCase.list(actor,
				new PurchaseOrderListQuery(supplierExternalId, productExternalId, status, from, to,
						Math.max(page, 0), resolveSize(size), sort));

		List<PurchaseOrderSummaryResponse> content = result.content().stream()
				.map(PurchaseOrderController::toSummaryResponse)
				.toList();
		return new PurchaseOrderPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/orders/{externalId}")
	public PurchaseOrderDetailResponse detail(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(queryPurchasesUseCase.detail(actor, externalId));
	}

	@PutMapping("/orders/{externalId}")
	public PurchaseOrderDetailResponse edit(@PathVariable UUID externalId,
			@Valid @RequestBody EditPurchaseOrderRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<PurchaseOrderLineCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new PurchaseOrderLineCommand(
						item.productExternalId(),
						item.quantity(),
						item.unitOfMeasureExternalId(),
						item.unitCost(),
						item.discountPercent()
				))
				.toList();

		EditPurchaseOrderCommand command = new EditPurchaseOrderCommand(
				request.supplierExternalId(),
				request.paymentTerms(),
				request.notes(),
				items
		);

		return toDetailResponse(managePurchaseOrdersUseCase.edit(actor, externalId, command));
	}

	@PostMapping("/orders/{externalId}/approval")
	public PurchaseOrderDetailResponse approve(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(transitionPurchaseOrderUseCase.approve(actor, externalId));
	}

	@PostMapping("/orders/{externalId}/cancellation")
	public PurchaseOrderDetailResponse cancel(@PathVariable UUID externalId,
			@RequestBody(required = false) CancellationRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		String reason = request != null ? request.reason() : null;
		return toDetailResponse(transitionPurchaseOrderUseCase.cancel(actor, externalId,
				new CancelPurchaseOrderCommand(reason)));
	}

	@PostMapping("/orders/{externalId}/receptions")
	public PurchaseOrderDetailResponse receive(@PathVariable UUID externalId,
			@Valid @RequestBody ReceivePurchaseRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<ReceptionItemCommand> items = request.items() == null ? List.of() : request.items().stream()
				.map(item -> new ReceptionItemCommand(
						item.itemExternalId(),
						item.receivedQuantity(),
						item.unitOfMeasureExternalId()
				))
				.toList();

		ReceivePurchaseCommand command = new ReceivePurchaseCommand(request.notes(), items);
		return toDetailResponse(receivePurchaseUseCase.receive(actor, externalId, command));
	}

	@GetMapping("/cost-history")
	public CostHistoryPageResponse costHistory(
			@RequestParam UUID productExternalId,
			@RequestParam(required = false) UUID supplierExternalId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		PurchasePage<CostHistoryEntry> result = queryPurchasesUseCase.costHistory(actor,
				new CostHistoryQuery(productExternalId, supplierExternalId, from, to,
						Math.max(page, 0), resolveSize(size)));

		List<CostHistoryEntryResponse> content = result.content().stream()
				.map(PurchaseOrderController::toCostHistoryEntryResponse)
				.toList();
		return new CostHistoryPageResponse(content, result.totalElements(), result.page(), result.size());
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

	private static PurchaseOrderDetailResponse toDetailResponse(PurchaseOrderDetail detail) {
		List<PurchaseOrderItemResponse> items = detail.items().stream()
				.map(PurchaseOrderController::toItemResponse)
				.toList();
		return new PurchaseOrderDetailResponse(
				detail.externalId(),
				detail.orderNumber(),
				detail.status(),
				toBranchRefResponse(detail.branch()),
				toSupplierRefResponse(detail.supplier()),
				toUserRefResponse(detail.createdBy()),
				detail.paymentTerms(),
				detail.totalAmount(),
				detail.notes(),
				detail.cancellationReason(),
				detail.createdAt(),
				detail.updatedAt(),
				detail.receivedAt(),
				items
		);
	}

	private static PurchaseOrderSummaryResponse toSummaryResponse(PurchaseOrderSummary summary) {
		return new PurchaseOrderSummaryResponse(
				summary.externalId(),
				summary.orderNumber(),
				summary.status(),
				toBranchRefResponse(summary.branch()),
				toSupplierRefResponse(summary.supplier()),
				summary.totalAmount(),
				summary.createdAt(),
				summary.receivedAt()
		);
	}

	private static CostHistoryEntryResponse toCostHistoryEntryResponse(CostHistoryEntry entry) {
		return new CostHistoryEntryResponse(
				entry.orderExternalId(),
				entry.orderNumber(),
				toSupplierRefResponse(entry.supplier()),
				entry.unitCost(),
				entry.discountPercent(),
				entry.effectiveUnitCost(),
				entry.quantity(),
				entry.orderedAt(),
				entry.receivedAt()
		);
	}

	private static BranchRefResponse toBranchRefResponse(BranchRef ref) {
		return ref == null ? null : new BranchRefResponse(ref.externalId(), ref.name());
	}

	private static SupplierRefResponse toSupplierRefResponse(SupplierRef ref) {
		return ref == null ? null : new SupplierRefResponse(ref.externalId(), ref.taxId(), ref.name());
	}

	private static UserRefResponse toUserRefResponse(UserRef ref) {
		return ref == null ? null : new UserRefResponse(ref.externalId(), ref.username());
	}

	private static PurchaseOrderItemResponse toItemResponse(PurchaseOrderItemView item) {
		return new PurchaseOrderItemResponse(
				item.externalId(),
				item.productExternalId(),
				item.sku(),
				item.name(),
				item.orderedQuantity(),
				item.receivedQuantity(),
				item.pendingQuantity(),
				item.unitCost(),
				item.discountPercent(),
				item.effectiveUnitCost(),
				item.subtotal()
		);
	}

	public record CreatePurchaseOrderRequest(
			@NotNull UUID supplierExternalId,
			String paymentTerms,
			String notes,
			@NotEmpty List<@Valid PurchaseOrderLineRequest> items
	) {
	}

	public record EditPurchaseOrderRequest(
			@NotNull UUID supplierExternalId,
			String paymentTerms,
			String notes,
			@NotEmpty List<@Valid PurchaseOrderLineRequest> items
	) {
	}

	public record PurchaseOrderLineRequest(
			@NotNull UUID productExternalId,
			@NotNull BigDecimal quantity,
			UUID unitOfMeasureExternalId,
			@NotNull BigDecimal unitCost,
			BigDecimal discountPercent
	) {
	}

	public record CancellationRequest(String reason) {
	}

	public record ReceivePurchaseRequest(
			String notes,
			@NotEmpty List<@Valid ReceptionItemRequest> items
	) {
	}

	public record ReceptionItemRequest(
			@NotNull UUID itemExternalId,
			@NotNull BigDecimal receivedQuantity,
			UUID unitOfMeasureExternalId
	) {
	}

	public record BranchRefResponse(UUID externalId, String name) {
	}

	public record SupplierRefResponse(UUID externalId, String taxId, String name) {
	}

	public record UserRefResponse(UUID externalId, String username) {
	}

	public record PurchaseOrderItemResponse(
			UUID externalId,
			UUID productExternalId,
			String sku,
			String name,
			BigDecimal orderedQuantity,
			BigDecimal receivedQuantity,
			BigDecimal pendingQuantity,
			BigDecimal unitCost,
			BigDecimal discountPercent,
			BigDecimal effectiveUnitCost,
			BigDecimal subtotal
	) {
	}

	public record PurchaseOrderDetailResponse(
			UUID externalId,
			String orderNumber,
			PurchaseOrderStatus status,
			BranchRefResponse branch,
			SupplierRefResponse supplier,
			UserRefResponse createdBy,
			String paymentTerms,
			BigDecimal totalAmount,
			String notes,
			String cancellationReason,
			Instant createdAt,
			Instant updatedAt,
			Instant receivedAt,
			List<PurchaseOrderItemResponse> items
	) {
	}

	public record PurchaseOrderSummaryResponse(
			UUID externalId,
			String orderNumber,
			PurchaseOrderStatus status,
			BranchRefResponse branch,
			SupplierRefResponse supplier,
			BigDecimal totalAmount,
			Instant createdAt,
			Instant receivedAt
	) {
	}

	public record PurchaseOrderPageResponse(
			List<PurchaseOrderSummaryResponse> content,
			long totalElements,
			int page,
			int size
	) {
	}

	public record CostHistoryEntryResponse(
			UUID orderExternalId,
			String orderNumber,
			SupplierRefResponse supplier,
			BigDecimal unitCost,
			BigDecimal discountPercent,
			BigDecimal effectiveUnitCost,
			BigDecimal quantity,
			Instant orderedAt,
			Instant receivedAt
	) {
	}

	public record CostHistoryPageResponse(
			List<CostHistoryEntryResponse> content,
			long totalElements,
			int page,
			int size
	) {
	}
}
