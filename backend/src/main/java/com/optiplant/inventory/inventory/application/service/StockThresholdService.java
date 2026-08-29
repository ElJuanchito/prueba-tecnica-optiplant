package com.optiplant.inventory.inventory.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optiplant.inventory.inventory.application.port.in.ManageStockThresholdUseCase;
import com.optiplant.inventory.inventory.application.port.out.AlertEventPublisherPort;
import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.ThresholdView;
import com.optiplant.inventory.inventory.domain.service.AlertRaisingPolicy;
import com.optiplant.inventory.inventory.domain.service.BranchScopePolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sets a product's minimum-stock threshold in the caller's own branch (CU-INV-07). Writes no
 * Kardex row (R-14); the resulting breach is evaluated on the committed threshold exactly as a
 * movement would (R-15), with no triggering movement to reference.
 *
 * <p>Deliberately <strong>not</strong> {@code @Service} yet — see {@code StockMovementService}'s
 * class Javadoc. {@code @Service} is added in S2, alongside the adapters that satisfy these ports.
 */
public class StockThresholdService implements ManageStockThresholdUseCase {

	private final BranchInventoryRepositoryPort branchInventoryRepository;
	private final AuditWritePort auditWritePort;
	private final AlertEventPublisherPort alertEventPublisherPort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public StockThresholdService(BranchInventoryRepositoryPort branchInventoryRepository,
			AuditWritePort auditWritePort, AlertEventPublisherPort alertEventPublisherPort) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.auditWritePort = auditWritePort;
		this.alertEventPublisherPort = alertEventPublisherPort;
	}

	@Override
	@Transactional
	public ThresholdView setThreshold(AuthenticatedPrincipal actor, UUID productExternalId,
			BigDecimal minStockThreshold) {
		UUID branchExternalId = BranchScopePolicy.resolveOwnBranch(actor);
		StockLevel threshold = new StockLevel(minStockThreshold);

		BranchInventory current = branchInventoryRepository.lockForUpdate(branchExternalId, productExternalId)
				.orElseGet(() -> branchInventoryRepository.createZeroed(branchExternalId, productExternalId));

		Instant now = Instant.now();
		BranchInventory saved = branchInventoryRepository.save(current.withThreshold(threshold, now));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), branchExternalId, "SET_STOCK_THRESHOLD",
				"branch_inventories", productExternalId.toString(), serializeThreshold(current.minStockThreshold()),
				serializeThreshold(saved.minStockThreshold()), null));

		AlertRaisingPolicy.evaluate(saved, null)
				.ifPresent(breach -> alertEventPublisherPort.publish(AlertRaisingPolicy.render(breach)));

		return new ThresholdView(productExternalId, saved.minStockThreshold().value());
	}

	private String serializeThreshold(StockLevel threshold) {
		try {
			return OBJECT_MAPPER.writeValueAsString(new ThresholdAuditPayload(threshold.value()));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record ThresholdAuditPayload(BigDecimal minStockThreshold) {
	}
}
