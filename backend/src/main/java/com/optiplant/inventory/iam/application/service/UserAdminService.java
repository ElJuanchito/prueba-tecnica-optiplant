package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase;
import com.optiplant.inventory.iam.application.port.out.PasswordHasherPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserFilter;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserUpdate;
import com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException;
import com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException;
import com.optiplant.inventory.iam.domain.exception.UserNotFoundException;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.iam.domain.service.BranchAccessPolicy;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates user administration (user-administration capability, RF-SEG-02
 * / CU-SEG-02): create, edit, disable, query. Every mutation writes its audit
 * entry through {@link AuditWritePort} inside the same transaction — the
 * synchronous-effects invariant CLAUDE.md requires (never {@code @Async} or
 * {@code AFTER_COMMIT}), the same pattern {@code AuditAtomicityFixtureService}
 * proved in slice 4.
 *
 * <p>{@code SecurityConfig}'s {@code /api/admin/users/**} matcher admits both
 * {@code ADMIN} and {@code BRANCH_MANAGER}; {@code OPERATOR} never reaches
 * this service (mirrors {@code AuditQueryService}'s rationale for skipping a
 * redundant service-level check). {@code ADMIN} manages any user in any
 * branch; {@code BRANCH_MANAGER} may only create/edit/disable {@code
 * OPERATOR} users within their own session branch — enforced per-call via
 * {@link #requireManageable}, which reuses {@link BranchAccessPolicy} (slice
 * 3) for the branch half of that check.
 */
@Service
public class UserAdminService implements ManageUsersUseCase {

	private final UserRepositoryPort userRepository;
	private final PasswordHasherPort passwordHasher;
	private final RefreshTokenRepositoryPort refreshTokenRepository;
	private final AuditWritePort auditWritePort;
	private final BranchAccessPolicy branchAccessPolicy = new BranchAccessPolicy();

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public UserAdminService(UserRepositoryPort userRepository, PasswordHasherPort passwordHasher,
			RefreshTokenRepositoryPort refreshTokenRepository, AuditWritePort auditWritePort) {
		this.userRepository = userRepository;
		this.passwordHasher = passwordHasher;
		this.refreshTokenRepository = refreshTokenRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional
	public UserAccount create(AuthenticatedPrincipal actor, CreateUserCommand command) {
		requireBranchForNonAdmin(command.role(), command.branchExternalId());
		requireManageable(actor, command.role(), command.branchExternalId());
		requireUniqueUsername(command.username(), null);
		requireUniqueEmail(command.email(), null);

		String passwordHash = passwordHasher.hash(command.password());
		UserAccount created = userRepository.create(new UserRepositoryPort.NewUser(command.username(),
				command.email(), passwordHash, command.fullName(), command.role(), command.branchExternalId()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), created.branchExternalId(), AuditAction.CREATE.name(),
				"users", created.externalId().toString(), null, serializePayload(created), null));
		return created;
	}

	@Override
	@Transactional
	public UserAccount edit(AuthenticatedPrincipal actor, UUID externalId, EditUserCommand command) {
		UserAccount existing = userRepository.findByExternalId(externalId)
				.orElseThrow(() -> new UserNotFoundException(externalId));
		requireManageable(actor, existing.role(), existing.branchExternalId());
		requireBranchForNonAdmin(command.role(), command.branchExternalId());
		requireManageable(actor, command.role(), command.branchExternalId());
		requireUniqueEmail(command.email(), externalId);

		UserAccount updated = userRepository.update(externalId,
				new UserUpdate(command.email(), command.fullName(), command.role(), command.branchExternalId()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), updated.branchExternalId(), AuditAction.UPDATE.name(),
				"users", externalId.toString(), serializePayload(existing), serializePayload(updated), null));
		return updated;
	}

	@Override
	@Transactional
	public void disable(AuthenticatedPrincipal actor, UUID externalId) {
		UserAccount target = userRepository.findByExternalId(externalId)
				.orElseThrow(() -> new UserNotFoundException(externalId));
		requireManageable(actor, target.role(), target.branchExternalId());

		userRepository.disable(externalId);
		// Same transaction as the disable itself — a live access token still
		// expires on its own within 15 min (P2); this closes every refresh
		// session across every device at once (P4).
		refreshTokenRepository.revokeAllForUser(externalId, RevocationReason.USER_DISABLED);

		UserAccount disabledTarget = new UserAccount(target.externalId(), target.username(), target.email(),
				target.passwordHash(), target.fullName(), target.role(), target.branchExternalId(), false);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), target.branchExternalId(), AuditAction.DISABLE.name(),
				"users", externalId.toString(), serializePayload(target), serializePayload(disabledTarget), null));
	}

	@Override
	public UserPage list(AuthenticatedPrincipal actor, UserQuery query) {
		// BRANCH_MANAGER only manages OPERATOR in their own branch — the query is
		// forced to that scope regardless of what they submit, mirroring
		// AuditQueryService's rationale for ADMIN's filter passing through unchanged.
		UUID effectiveBranch = actor.role() == Role.BRANCH_MANAGER ? actor.branchId() : query.branchExternalId();
		Role effectiveRole = actor.role() == Role.BRANCH_MANAGER ? Role.OPERATOR : query.role();
		return userRepository
				.list(new UserFilter(query.active(), effectiveRole, effectiveBranch, query.page(), query.size()));
	}

	// user-administration "User creation assigns a unique username, unique
	// email, a role, and a branch": a BRANCH_MANAGER/OPERATOR must be created
	// (or edited) with a branch_id; only ADMIN may have branch_id = null.
	private void requireBranchForNonAdmin(Role role, UUID branchExternalId) {
		if (role != Role.ADMIN && branchExternalId == null) {
			throw new IllegalArgumentException(
					"role " + role + " requires a branch assignment (only ADMIN may be branchless)");
		}
	}

	// ADMIN manages any user in any branch. BRANCH_MANAGER may only manage a
	// user whose role is OPERATOR, and only within their own session branch —
	// checked against both the pre-existing target (edit/disable) and the
	// requested new role/branch (create/edit), so a BRANCH_MANAGER can neither
	// promote an OPERATOR out of that role nor move them to another branch.
	private void requireManageable(AuthenticatedPrincipal actor, Role targetRole, UUID targetBranchExternalId) {
		branchAccessPolicy.requireMayMutate(actor, targetBranchExternalId);
		if (actor.role() == Role.BRANCH_MANAGER && targetRole != Role.OPERATOR) {
			throw new CrossBranchMutationException(
					"BRANCH_MANAGER solo puede gestionar usuarios OPERATOR de su propia sucursal");
		}
	}

	private void requireUniqueUsername(String username, UUID excludeExternalId) {
		userRepository.findByUsername(username)
				.filter(existing -> !existing.externalId().equals(excludeExternalId))
				.ifPresent(existing -> {
					throw new DuplicateUsernameException("username '" + username + "' is already in use");
				});
	}

	private void requireUniqueEmail(String email, UUID excludeExternalId) {
		userRepository.findByEmail(email)
				.filter(existing -> !existing.externalId().equals(excludeExternalId))
				.ifPresent(existing -> {
					throw new DuplicateUsernameException("email '" + email + "' is already in use");
				});
	}

	private String serializePayload(UserAccount user) {
		if (user == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(UserAuditPayload.from(user));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record UserAuditPayload(String role, UUID branchExternalId, boolean active, String email, String fullName) {
		static UserAuditPayload from(UserAccount user) {
			return new UserAuditPayload(user.role().name(), user.branchExternalId(), user.active(), user.email(),
					user.fullName());
		}
	}
}
