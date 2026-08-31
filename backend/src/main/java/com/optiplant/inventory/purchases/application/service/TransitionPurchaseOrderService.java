package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.in.TransitionPurchaseOrderUseCase;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.service.PurchaseAccessPolicy;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates approving or cancelling a purchase order (CU-COM-03, RF-COM-05, RN-15, design §4,
 * §7). The {@code purchase_orders} row is locked before its {@code status} is read (F-5, design
 * §10 trap 3); cancellation from {@code PARTIALLY_RECEIVED} reverses no stock and writes no
 * Kardex row (R-13, PA-08).
 *
 * <p><strong>Ships without {@code @Service}</strong> while its out-ports have no adapter (S1,
 * design §10 trap 4). S2 task 2.6 restores the stereotype.
 */
@Transactional
public class TransitionPurchaseOrderService implements TransitionPurchaseOrderUseCase {

	private static final String ENTITY_NAME = "PURCHASE_ORDER";

	private final PurchaseOrderRepositoryPort orderRepository;
	private final PurchaseReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public TransitionPurchaseOrderService(PurchaseOrderRepositoryPort orderRepository,
			PurchaseReferencePort referencePort, AuditWritePort auditWritePort) {
		this.orderRepository = orderRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public PurchaseOrderDetail approve(AuthenticatedPrincipal actor, UUID externalId) {
		PurchaseOrder locked = lockVisible(actor, externalId);
		PurchaseOrder saved = orderRepository.save(locked.approve(Instant.now()));

		audit(actor, "APPROVE_PURCHASE_ORDER", saved);
		return PurchaseOrderDetailAssembler.toDetail(saved, referencePort);
	}

	@Override
	public PurchaseOrderDetail cancel(AuthenticatedPrincipal actor, UUID externalId,
			CancelPurchaseOrderCommand command) {
		PurchaseOrder locked = lockVisible(actor, externalId);
		String reason = command != null ? command.reason() : null;
		PurchaseOrder saved = orderRepository.save(locked.cancel(reason, Instant.now()));

		audit(actor, "CANCEL_PURCHASE_ORDER", saved);
		return PurchaseOrderDetailAssembler.toDetail(saved, referencePort);
	}

	private PurchaseOrder lockVisible(AuthenticatedPrincipal actor, UUID externalId) {
		PurchaseOrder locked = orderRepository.lockForUpdate(externalId)
				.orElseThrow(() -> new PurchaseOrderNotFoundException(externalId));
		PurchaseAccessPolicy.assertVisible(actor, locked);
		return locked;
	}

	private void audit(AuthenticatedPrincipal actor, String action, PurchaseOrder order) {
		auditWritePort.record(new AuditEntryCommand(actor.userId(), order.branchExternalId(), action, ENTITY_NAME,
				order.externalId().toString(), null, null, null));
	}
}
