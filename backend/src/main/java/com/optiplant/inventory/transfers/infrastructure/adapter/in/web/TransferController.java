package com.optiplant.inventory.transfers.infrastructure.adapter.in.web;

import com.optiplant.inventory.transfers.application.port.in.CancelTransferUseCase;
import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase;
import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase.DispatchCommand;
import com.optiplant.inventory.transfers.application.port.in.DispatchTransferUseCase.DispatchLineCommand;
import com.optiplant.inventory.transfers.application.port.in.QueryTransfersUseCase;
import com.optiplant.inventory.transfers.application.port.in.QueryTransfersUseCase.TransferListQuery;
import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase;
import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase.ReceiptCommand;
import com.optiplant.inventory.transfers.application.port.in.ReceiveTransferUseCase.ReceiptLineCommand;
import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase;
import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase.RequestTransferCommand;
import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase.RequestedLine;
import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase;
import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase.ApprovalCommand;
import com.optiplant.inventory.transfers.application.port.in.ReviewTransferUseCase.ApprovedLineCommand;
import com.optiplant.inventory.transfers.domain.model.BranchReference;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferDirection;
import com.optiplant.inventory.transfers.domain.model.TransferItemView;
import com.optiplant.inventory.transfers.domain.model.TransferPage;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.transfers.domain.model.TransferSummary;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/transfers/**} — the eight endpoints of contract §6. No endpoint accepts a branch
 * identifier in path, query or body (RN-14): the acting branch is always session-derived, deeper
 * inside the application services (destination at request, origin/destination read from the
 * stored transfer for every transition). Page size is rejected above {@value #MAX_PAGE_SIZE},
 * never clamped (R-00). No numeric id and no raw F-1 priority token ever appear in a response —
 * {@link TransferDetail#priority()} and {@link TransferDetail#observations()} already carry the
 * token stripped (design §3.5).
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final RequestTransferUseCase requestTransferUseCase;
	private final ReviewTransferUseCase reviewTransferUseCase;
	private final DispatchTransferUseCase dispatchTransferUseCase;
	private final ReceiveTransferUseCase receiveTransferUseCase;
	private final CancelTransferUseCase cancelTransferUseCase;
	private final QueryTransfersUseCase queryTransfersUseCase;
	private final PrincipalAccessor principalAccessor;

	public TransferController(RequestTransferUseCase requestTransferUseCase, ReviewTransferUseCase reviewTransferUseCase,
			DispatchTransferUseCase dispatchTransferUseCase, ReceiveTransferUseCase receiveTransferUseCase,
			CancelTransferUseCase cancelTransferUseCase, QueryTransfersUseCase queryTransfersUseCase,
			PrincipalAccessor principalAccessor) {
		this.requestTransferUseCase = requestTransferUseCase;
		this.reviewTransferUseCase = reviewTransferUseCase;
		this.dispatchTransferUseCase = dispatchTransferUseCase;
		this.receiveTransferUseCase = receiveTransferUseCase;
		this.cancelTransferUseCase = cancelTransferUseCase;
		this.queryTransfersUseCase = queryTransfersUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public ResponseEntity<TransferDetailResponse> request(@Valid @RequestBody RequestTransferRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<RequestedLine> items = request.items().stream()
				.map(line -> new RequestedLine(line.productExternalId(), line.requestedQuantity())).toList();
		TransferDetail detail = requestTransferUseCase.request(actor,
				new RequestTransferCommand(request.originBranchExternalId(), request.priority(), request.notes(), items));
		return ResponseEntity.status(HttpStatus.CREATED).body(toDetailResponse(detail));
	}

	@GetMapping
	public TransferPageResponse list(@RequestParam(required = false) TransferStatus status,
			@RequestParam(required = false) TransferDirection direction, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		TransferPage result = queryTransfersUseCase.list(actor,
				new TransferListQuery(status, direction, from, to, Math.max(page, 0), resolveSize(size), sort));
		List<TransferSummaryResponse> content = result.content().stream().map(TransferController::toSummaryResponse)
				.toList();
		return new TransferPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@GetMapping("/{externalId}")
	public TransferDetailResponse detail(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(queryTransfersUseCase.detail(actor, externalId));
	}

	@PostMapping("/{externalId}/approval")
	public TransferDetailResponse approve(@PathVariable UUID externalId, @Valid @RequestBody ApprovalRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<ApprovedLineCommand> items = request.items().stream()
				.map(line -> new ApprovedLineCommand(line.itemExternalId(), line.approvedQuantity())).toList();
		return toDetailResponse(
				reviewTransferUseCase.approve(actor, externalId, new ApprovalCommand(items, request.notes())));
	}

	@PostMapping("/{externalId}/rejection")
	public TransferDetailResponse reject(@PathVariable UUID externalId, @Valid @RequestBody ReasonRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(reviewTransferUseCase.reject(actor, externalId, request.reason()));
	}

	@PostMapping("/{externalId}/dispatch")
	public TransferDetailResponse dispatch(@PathVariable UUID externalId, @Valid @RequestBody DispatchRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<DispatchLineCommand> items = request.items().stream()
				.map(line -> new DispatchLineCommand(line.itemExternalId(), line.dispatchedQuantity())).toList();
		DispatchCommand command = new DispatchCommand(request.carrierName(), request.trackingNumber(),
				request.estimatedArrivalAt(), items);
		return toDetailResponse(dispatchTransferUseCase.dispatch(actor, externalId, command));
	}

	@PostMapping("/{externalId}/receipt")
	public TransferDetailResponse receive(@PathVariable UUID externalId, @Valid @RequestBody ReceiptRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		List<ReceiptLineCommand> items = request.items().stream().map(
				line -> new ReceiptLineCommand(line.itemExternalId(), line.receivedQuantity(), line.discrepancyReason()))
				.toList();
		return toDetailResponse(receiveTransferUseCase.receive(actor, externalId, new ReceiptCommand(items)));
	}

	@PostMapping("/{externalId}/cancellation")
	public TransferDetailResponse cancel(@PathVariable UUID externalId, @Valid @RequestBody ReasonRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toDetailResponse(cancelTransferUseCase.cancel(actor, externalId, request.reason()));
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

	private static TransferDetailResponse toDetailResponse(TransferDetail detail) {
		List<TransferItemResponse> items = detail.items().stream().map(TransferController::toItemResponse).toList();
		return new TransferDetailResponse(detail.externalId(), detail.number().value(), detail.status(),
				detail.priority(), toBranchResponse(detail.originBranch()), toBranchResponse(detail.destinationBranch()),
				detail.carrierName() == null ? null : detail.carrierName().value(), detail.trackingNumber(),
				detail.dispatchedAt(), detail.estimatedArrivalAt(), detail.actualArrivalAt(), detail.deviationHours(),
				detail.observations(), detail.requestedByUserExternalId(), detail.dispatchedByUserExternalId(),
				detail.receivedByUserExternalId(), detail.createdAt(), detail.updatedAt(), items);
	}

	private static TransferSummaryResponse toSummaryResponse(TransferSummary summary) {
		return new TransferSummaryResponse(summary.externalId(), summary.number().value(), summary.status(),
				summary.priority(), toBranchResponse(summary.originBranch()), toBranchResponse(summary.destinationBranch()),
				summary.createdAt(), summary.estimatedArrivalAt());
	}

	private static BranchReferenceResponse toBranchResponse(BranchReference branch) {
		return branch == null ? null : new BranchReferenceResponse(branch.externalId(), branch.name());
	}

	private static TransferItemResponse toItemResponse(TransferItemView item) {
		return new TransferItemResponse(item.externalId(), item.productExternalId(), item.sku(), item.name(),
				item.requestedQuantity(), item.dispatchedQuantity(), item.receivedQuantity(),
				item.discrepancyQuantity(), item.discrepancyReason());
	}

	public record RequestTransferRequest(@NotNull UUID originBranchExternalId, @NotNull TransferPriority priority,
			String notes, @NotEmpty List<RequestedLineRequest> items) {
	}

	public record RequestedLineRequest(@NotNull UUID productExternalId, @NotNull BigDecimal requestedQuantity) {
	}

	public record ApprovalRequest(@NotEmpty List<ApprovedLineRequest> items, String notes) {
	}

	public record ApprovedLineRequest(@NotNull UUID itemExternalId, @NotNull BigDecimal approvedQuantity) {
	}

	// Bean validation covers structural presence only; a blank reason still reaches TransferReason
	// and raises TransferReasonRequiredException (R-09/R-21), not the generic invalid_request.
	public record ReasonRequest(String reason) {
	}

	public record DispatchRequest(@NotBlank String carrierName, String trackingNumber, Instant estimatedArrivalAt,
			@NotEmpty List<DispatchLineRequest> items) {
	}

	public record DispatchLineRequest(@NotNull UUID itemExternalId, @NotNull BigDecimal dispatchedQuantity) {
	}

	public record ReceiptRequest(@NotEmpty List<ReceiptLineRequest> items) {
	}

	public record ReceiptLineRequest(@NotNull UUID itemExternalId, @NotNull BigDecimal receivedQuantity,
			String discrepancyReason) {
	}

	public record BranchReferenceResponse(UUID externalId, String name) {
	}

	public record TransferItemResponse(UUID externalId, UUID productExternalId, String sku, String name,
			BigDecimal requestedQuantity, BigDecimal dispatchedQuantity, BigDecimal receivedQuantity,
			BigDecimal discrepancyQuantity, String discrepancyReason) {
	}

	public record TransferDetailResponse(UUID externalId, String transferNumber, TransferStatus status,
			TransferPriority priority, BranchReferenceResponse originBranch, BranchReferenceResponse destinationBranch,
			String carrierName, String trackingNumber, Instant dispatchedAt, Instant estimatedArrivalAt,
			Instant actualArrivalAt, BigDecimal deviationHours, List<String> observations, UUID requestedBy,
			UUID dispatchedBy, UUID receivedBy, Instant createdAt, Instant updatedAt, List<TransferItemResponse> items) {
	}

	public record TransferSummaryResponse(UUID externalId, String transferNumber, TransferStatus status,
			TransferPriority priority, BranchReferenceResponse originBranch, BranchReferenceResponse destinationBranch,
			Instant createdAt, Instant estimatedArrivalAt) {
	}

	public record TransferPageResponse(List<TransferSummaryResponse> content, long totalElements, int page, int size) {
	}
}
