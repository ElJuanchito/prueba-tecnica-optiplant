package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.BranchFilter;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.BranchPage;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.BranchUpdate;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.NewBranch;
import com.optiplant.inventory.iam.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.iam.domain.exception.DuplicateBranchCodeException;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates branch administration (branch-administration capability, RF-SEG-03 /
 * CU-SEG-03): create, edit, disable, query. Every mutation writes its audit entry
 * through {@link AuditWritePort} inside the same transaction — the
 * synchronous-effects invariant CLAUDE.md requires (never {@code @Async} or
 * {@code AFTER_COMMIT}), mirroring {@code UserAdminService}.
 *
 * <p>For each audit entry, {@code branch_id} is the branch of the affected resource
 * itself (its own {@code external_id}), matching {@code UserAdminService}'s pattern.
 */
@Service
public class BranchAdminService implements ManageBranchesUseCase {

	private final BranchRepositoryPort branchRepository;
	private final AuditWritePort auditWritePort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public BranchAdminService(BranchRepositoryPort branchRepository, AuditWritePort auditWritePort) {
		this.branchRepository = branchRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public BranchProfile create(AuthenticatedPrincipal actor, CreateBranchCommand command) {
		requireUniqueCode(command.code());

		BranchProfile created = branchRepository.create(new NewBranch(command.code(), command.name(),
				command.address(), command.city(), command.phone()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), created.externalId(), AuditAction.CREATE.name(),
				"branches", created.externalId().toString(), null, serializePayload(created), null));
		return created;
	}

	@Override
	@Transactional
	public BranchProfile edit(AuthenticatedPrincipal actor, UUID externalId, EditBranchCommand command) {
		BranchProfile existing = branchRepository.findByExternalId(externalId)
				.orElseThrow(() -> new BranchNotFoundException(externalId));

		BranchProfile updated = branchRepository.update(externalId,
				new BranchUpdate(command.name(), command.address(), command.city(), command.phone()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), updated.externalId(), AuditAction.UPDATE.name(),
				"branches", externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public void disable(AuthenticatedPrincipal actor, UUID externalId) {
		BranchProfile target = branchRepository.findByExternalId(externalId)
				.orElseThrow(() -> new BranchNotFoundException(externalId));

		branchRepository.disable(externalId);

		BranchProfile disabledTarget = new BranchProfile(target.externalId(), target.code(), target.name(),
				target.address(), target.city(), target.phone(), false);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), target.externalId(), AuditAction.DISABLE.name(),
				"branches", externalId.toString(), serializePayload(target), serializePayload(disabledTarget), null));
	}

	@Override
	public BranchPage list(BranchQuery query) {
		return branchRepository.list(new BranchFilter(query.active(), query.page(), query.size()));
	}

	private void requireUniqueCode(String code) {
		branchRepository.findByCode(code).ifPresent(existing -> {
			throw new DuplicateBranchCodeException("branch code '" + code + "' is already in use");
		});
	}

	private String serializePayload(BranchProfile branch) {
		if (branch == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(BranchAuditPayload.from(branch));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record BranchAuditPayload(String code, String name, String address, String city, String phone,
			boolean active) {
		static BranchAuditPayload from(BranchProfile branch) {
			return new BranchAuditPayload(branch.code(), branch.name(), branch.address(), branch.city(),
					branch.phone(), branch.active());
		}
	}
}
