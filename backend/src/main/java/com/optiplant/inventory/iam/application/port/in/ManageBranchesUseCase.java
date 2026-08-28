package com.optiplant.inventory.iam.application.port.in;

import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.BranchPage;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Create, edit, disable, and query branches — {@code ADMIN}-only (enforced by
 * {@code SecurityConfig}'s {@code /api/admin/branches/**} matcher, slice 3;
 * mirrors {@code ManageUsersUseCase}). Every mutation writes an audit entry in the
 * same transaction (CLAUDE.md's synchronous-effects invariant).
 */
public interface ManageBranchesUseCase {

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.DuplicateBranchCodeException
	 *     on a duplicate {@code code}
	 */
	BranchProfile create(AuthenticatedPrincipal actor, CreateBranchCommand command);

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.BranchNotFoundException
	 *     when {@code externalId} names no branch
	 */
	BranchProfile edit(AuthenticatedPrincipal actor, UUID externalId, EditBranchCommand command);

	/**
	 * Sets {@code is_active = false}. Never a physical delete (branch-administration
	 * "Branch disable is logical, never physical").
	 *
	 * @throws com.optiplant.inventory.iam.domain.exception.BranchNotFoundException
	 *     when {@code externalId} names no branch
	 */
	void disable(AuthenticatedPrincipal actor, UUID externalId);

	BranchPage list(BranchQuery query);

	record CreateBranchCommand(String code, String name, String address, String city, String phone) {
	}

	/** {@code code} is absent — edit never changes it. */
	record EditBranchCommand(String name, String address, String city, String phone) {
	}

	record BranchQuery(Boolean active, int page, int size) {
	}
}
