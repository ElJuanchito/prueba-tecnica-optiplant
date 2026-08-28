package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase;
import com.optiplant.inventory.iam.application.port.out.PasswordHasherPort;
import com.optiplant.inventory.iam.application.port.out.RefreshTokenRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserFilter;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserUpdate;
import com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException;
import com.optiplant.inventory.iam.domain.exception.UserNotFoundException;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
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
 * <p>No {@code BranchAccessPolicy} check here: {@code SecurityConfig}'s {@code
 * /api/admin/users/**} matcher already restricts every endpoint to {@code
 * ADMIN} (user-administration "Only ADMIN manages users"), and an {@code
 * ADMIN} is corporate-wide by {@link AuthenticatedPrincipal#mayMutateBranch}
 * — mirroring {@code AuditQueryService}'s rationale for skipping a redundant
 * service-level {@code OPERATOR} check.
 */
@Service
public class UserAdminService implements ManageUsersUseCase {

	private final UserRepositoryPort userRepository;
	private final PasswordHasherPort passwordHasher;
	private final RefreshTokenRepositoryPort refreshTokenRepository;
	private final AuditWritePort auditWritePort;

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
		requireUniqueUsername(command.username(), null);
		requireUniqueEmail(command.email(), null);

		String passwordHash = passwordHasher.hash(command.password());
		UserAccount created = userRepository.create(new UserRepositoryPort.NewUser(command.username(),
				command.email(), passwordHash, command.fullName(), command.role(), command.branchExternalId()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), actor.branchId(), AuditAction.CREATE.name(),
				"users", created.externalId().toString(), null, null, null));
		return created;
	}

	@Override
	@Transactional
	public UserAccount edit(AuthenticatedPrincipal actor, UUID externalId, EditUserCommand command) {
		userRepository.findByExternalId(externalId).orElseThrow(() -> new UserNotFoundException(externalId));
		requireBranchForNonAdmin(command.role(), command.branchExternalId());
		requireUniqueEmail(command.email(), externalId);

		UserAccount updated = userRepository.update(externalId,
				new UserUpdate(command.email(), command.fullName(), command.role(), command.branchExternalId()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), actor.branchId(), AuditAction.UPDATE.name(),
				"users", externalId.toString(), null, null, null));
		return updated;
	}

	@Override
	@Transactional
	public void disable(AuthenticatedPrincipal actor, UUID externalId) {
		userRepository.findByExternalId(externalId).orElseThrow(() -> new UserNotFoundException(externalId));

		userRepository.disable(externalId);
		// Same transaction as the disable itself — a live access token still
		// expires on its own within 15 min (P2); this closes every refresh
		// session across every device at once (P4).
		refreshTokenRepository.revokeAllForUser(externalId, RevocationReason.USER_DISABLED);

		auditWritePort.record(new AuditEntryCommand(actor.userId(), actor.branchId(), AuditAction.DISABLE.name(),
				"users", externalId.toString(), null, null, null));
	}

	@Override
	public UserPage list(UserQuery query) {
		return userRepository
				.list(new UserFilter(query.active(), query.role(), query.branchExternalId(), query.page(), query.size()));
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
}
