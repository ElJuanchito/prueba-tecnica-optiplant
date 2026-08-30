package com.optiplant.inventory.transfers.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase.RequestTransferCommand;
import com.optiplant.inventory.transfers.application.port.in.RequestTransferUseCase.RequestedLine;
import com.optiplant.inventory.transfers.application.port.out.TransferReferencePort;
import com.optiplant.inventory.transfers.application.port.out.TransferRepositoryPort;
import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.transfers.domain.exception.SameBranchTransferException;
import com.optiplant.inventory.transfers.domain.model.ProductReference;
import com.optiplant.inventory.transfers.domain.model.Transfer;
import com.optiplant.inventory.transfers.domain.model.TransferDetail;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferNotes;
import com.optiplant.inventory.transfers.domain.model.TransferNumber;
import com.optiplant.inventory.transfers.domain.model.TransferPriority;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.model.TransferStatus;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RequestTransferService} (CU-TRA-01): the destination is the session
 * branch (R-05), origin/items are validated before any write (R-03), and every mutation writes an
 * audit entry (task 1.11).
 */
@ExtendWith(MockitoExtension.class)
class RequestTransferServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	@Mock
	private TransferRepositoryPort transferRepository;
	@Mock
	private TransferReferencePort referencePort;
	@Mock
	private AuditWritePort auditWritePort;

	private RequestTransferService service;
	private UUID destinationBranch;
	private UUID originBranch;
	private AuthenticatedPrincipal actor;

	@BeforeEach
	void setUp() {
		service = new RequestTransferService(transferRepository, referencePort, auditWritePort);
		destinationBranch = UUID.randomUUID();
		originBranch = UUID.randomUUID();
		actor = new AuthenticatedPrincipal(UUID.randomUUID(), "operator", Role.OPERATOR, destinationBranch);
	}

	@Test
	void requestingATransferWritesAnAuditEntryScopedToTheDestinationBranch() {
		UUID productId = UUID.randomUUID();
		when(referencePort.findProduct(productId))
				.thenReturn(Optional.of(new ProductReference(productId, "SKU-1", "Widget")));
		Transfer created = createdTransfer(originBranch, destinationBranch);
		when(transferRepository.create(any())).thenReturn(created);
		when(referencePort.findBranches(any())).thenReturn(Map.of());
		when(referencePort.findProducts(any())).thenReturn(Map.of());

		RequestTransferCommand command = new RequestTransferCommand(originBranch, TransferPriority.STANDARD, null,
				List.of(new RequestedLine(productId, new BigDecimal("10"))));

		TransferDetail detail = service.request(actor, command);

		assertThat(detail).isNotNull();
		verify(referencePort).requireActiveBranch(originBranch);
		verify(auditWritePort).record(new AuditEntryCommand(actor.userId(), destinationBranch, "REQUEST_TRANSFER",
				"transfers", created.externalId().toString(), null, null, null));
	}

	@Test
	void originEqualToDestinationIsRefusedAndNothingIsWritten() {
		RequestTransferCommand command = new RequestTransferCommand(destinationBranch, TransferPriority.STANDARD, null,
				List.of(new RequestedLine(UUID.randomUUID(), BigDecimal.TEN)));

		assertThatThrownBy(() -> service.request(actor, command)).isInstanceOf(SameBranchTransferException.class);
		verify(transferRepository, never()).create(any());
		verifyNoInteractions(auditWritePort);
	}

	@Test
	void theSameProductTwiceIsRefused() {
		UUID productId = UUID.randomUUID();
		when(referencePort.findProduct(productId))
				.thenReturn(Optional.of(new ProductReference(productId, "SKU-1", "Widget")));
		RequestTransferCommand command = new RequestTransferCommand(originBranch, TransferPriority.STANDARD, null,
				List.of(new RequestedLine(productId, BigDecimal.TEN), new RequestedLine(productId, BigDecimal.ONE)));

		assertThatThrownBy(() -> service.request(actor, command)).isInstanceOf(DuplicateTransferItemException.class);
		verify(transferRepository, never()).create(any());
	}

	@Test
	void anUnknownOrDisabledProductIsRefused() {
		UUID productId = UUID.randomUUID();
		when(referencePort.findProduct(productId)).thenReturn(Optional.empty());

		RequestTransferCommand command = new RequestTransferCommand(originBranch, TransferPriority.STANDARD, null,
				List.of(new RequestedLine(productId, BigDecimal.TEN)));

		assertThatThrownBy(() -> service.request(actor, command)).isInstanceOf(ProductNotFoundException.class);
		verify(transferRepository, never()).create(any());
	}

	private static Transfer createdTransfer(UUID origin, UUID destination) {
		TransferItem item = TransferItem.requested(UUID.randomUUID(), UUID.randomUUID(),
				new TransferQuantity(BigDecimal.TEN));
		return new Transfer(UUID.randomUUID(), new TransferNumber("TRF-2026-0001"), TransferStatus.REQUESTED, origin,
				destination, UUID.randomUUID(), null, null, null, null, null, null, null,
				TransferNotes.empty(TransferPriority.STANDARD), NOW, NOW, List.of(item));
	}
}
