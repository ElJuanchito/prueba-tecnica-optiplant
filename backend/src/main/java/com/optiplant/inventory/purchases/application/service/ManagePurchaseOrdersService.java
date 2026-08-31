package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.in.ManagePurchaseOrdersUseCase;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.NewPurchaseOrder;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.NewPurchaseOrderItem;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.ProductUnitRef;
import com.optiplant.inventory.purchases.application.port.out.SupplierRepositoryPort;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotActiveException;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderNotes;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderTransition;
import com.optiplant.inventory.purchases.domain.model.Supplier;
import com.optiplant.inventory.purchases.domain.service.PurchaseAccessPolicy;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.PricedBasket;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.PricedLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderBasketPolicy.RawLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseOrderStateMachine;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates purchase order creation and editing (CU-COM-02, RF-COM-01, design §4, §7). The
 * acting branch is derived from the session (RN-14); totals are computed server-side (R-06).
 */
@Service
@Transactional
public class ManagePurchaseOrdersService implements ManagePurchaseOrdersUseCase {

	private static final String ENTITY_NAME = "PURCHASE_ORDER";

	private final PurchaseOrderRepositoryPort orderRepository;
	private final SupplierRepositoryPort supplierRepository;
	private final PurchaseReferencePort referencePort;
	private final AuditWritePort auditWritePort;

	public ManagePurchaseOrdersService(PurchaseOrderRepositoryPort orderRepository,
			SupplierRepositoryPort supplierRepository, PurchaseReferencePort referencePort,
			AuditWritePort auditWritePort) {
		this.orderRepository = orderRepository;
		this.supplierRepository = supplierRepository;
		this.referencePort = referencePort;
		this.auditWritePort = auditWritePort;
	}

	@Override
	public PurchaseOrderDetail create(AuthenticatedPrincipal actor, CreatePurchaseOrderCommand command) {
		UUID branchExternalId = PurchaseAccessPolicy.resolveActingBranch(actor);

		Supplier supplier = supplierRepository.findByExternalId(command.supplierExternalId())
				.orElseThrow(() -> new SupplierNotFoundException(command.supplierExternalId()));
		if (!supplier.active()) {
			throw new SupplierNotActiveException(supplier.externalId());
		}

		PricedBasket basket = priceBasket(command.items());
		PurchaseOrderNotes notes = command.notes() != null
				? PurchaseOrderNotes.fromHumanNote(command.notes())
				: PurchaseOrderNotes.empty();

		PurchaseOrder created = orderRepository.create(new NewPurchaseOrder(branchExternalId, supplier.externalId(),
				actor.userId(), command.paymentTerms(), notes, basket.totalAmount(), toNewItems(basket)));

		audit(actor, "CREATE_PURCHASE_ORDER", created);
		return PurchaseOrderDetailAssembler.toDetail(created, referencePort);
	}

	@Override
	public PurchaseOrderDetail edit(AuthenticatedPrincipal actor, UUID externalId,
			EditPurchaseOrderCommand command) {
		PurchaseOrder locked = orderRepository.lockForUpdate(externalId)
				.orElseThrow(() -> new PurchaseOrderNotFoundException(externalId));
		PurchaseAccessPolicy.assertVisible(actor, locked);
		PurchaseOrderStateMachine.require(locked.status(), PurchaseOrderTransition.EDIT);

		Supplier supplier = supplierRepository.findByExternalId(command.supplierExternalId())
				.orElseThrow(() -> new SupplierNotFoundException(command.supplierExternalId()));
		if (!supplier.active()) {
			throw new SupplierNotActiveException(supplier.externalId());
		}

		PricedBasket basket = priceBasket(command.items());
		PurchaseOrderNotes notes = command.notes() != null
				? PurchaseOrderNotes.fromHumanNote(command.notes())
				: PurchaseOrderNotes.empty();

		Instant now = Instant.now();
		PurchaseOrder updated = locked.withEdit(supplier.externalId(), command.paymentTerms(), notes,
				toDomainItems(basket), basket.totalAmount(), now);
		PurchaseOrder saved = orderRepository.replaceItems(updated, toNewItems(basket), basket.totalAmount());

		audit(actor, "UPDATE_PURCHASE_ORDER", saved);
		return PurchaseOrderDetailAssembler.toDetail(saved, referencePort);
	}

	private PricedBasket priceBasket(List<PurchaseOrderLineCommand> items) {
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("a purchase order must contain at least one item");
		}

		List<UUID> productIds = items.stream().map(PurchaseOrderLineCommand::productExternalId).toList();
		referencePort.requireActiveProducts(productIds);

		List<ProductUnitRef> unitRefs = items.stream()
				.filter(item -> item.unitOfMeasureExternalId() != null)
				.map(item -> new ProductUnitRef(item.productExternalId(), item.unitOfMeasureExternalId()))
				.toList();
		Map<UUID, BigDecimal> factors = unitRefs.isEmpty() ? Map.of() : referencePort.conversionFactors(unitRefs);

		List<RawLine> rawLines = items.stream()
				.map(item -> new RawLine(item.productExternalId(), item.quantity(), item.unitOfMeasureExternalId(),
						item.unitCost(), item.discountPercent()))
				.toList();
		return PurchaseOrderBasketPolicy.validateAndPrice(rawLines, factors);
	}

	private static List<PurchaseOrderItem> toDomainItems(PricedBasket basket) {
		return basket.lines().stream()
				.map(line -> new PurchaseOrderItem(UUID.randomUUID(), line.productExternalId(),
						line.orderedQuantity(), BigDecimal.ZERO, line.unitCost(), line.discountPercent(),
						line.subtotal()))
				.toList();
	}

	private static List<NewPurchaseOrderItem> toNewItems(PricedBasket basket) {
		return basket.lines().stream()
				.map((PricedLine line) -> new NewPurchaseOrderItem(line.productExternalId(), line.orderedQuantity(),
						line.unitCost(), line.discountPercent(), line.subtotal()))
				.toList();
	}

	private void audit(AuthenticatedPrincipal actor, String action, PurchaseOrder order) {
		auditWritePort.record(new AuditEntryCommand(actor.userId(), order.branchExternalId(), action, ENTITY_NAME,
				order.externalId().toString(), null, null, null));
	}
}
