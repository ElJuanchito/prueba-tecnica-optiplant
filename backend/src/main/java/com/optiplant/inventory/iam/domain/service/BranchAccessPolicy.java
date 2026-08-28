package com.optiplant.inventory.iam.domain.service;

import com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Enforces RN-08 + RN-14 at the point of mutation: a caller may only mutate a
 * resource belonging to their own session branch, unless they are a corporate
 * {@link com.optiplant.inventory.shared.security.Role#ADMIN}. Thin wrapper
 * around {@link AuthenticatedPrincipal#mayMutateBranch(UUID)} so every module
 * shares one rejection type and message instead of each controller
 * re-implementing the same {@code if} — pure domain logic, no I/O, no
 * framework dependency (design's {@code BranchAccessPolicy}, mirrors {@code
 * RefreshTokenPolicy}'s "new-able, not a Spring bean" shape).
 */
public class BranchAccessPolicy {

	/**
	 * @throws CrossBranchMutationException when {@code principal} may not mutate
	 *     a resource belonging to {@code targetBranchId}
	 */
	public void requireMayMutate(AuthenticatedPrincipal principal, UUID targetBranchId) {
		if (!principal.mayMutateBranch(targetBranchId)) {
			throw new CrossBranchMutationException(
					"El principal autenticado no puede mutar un recurso de una sucursal distinta a la propia");
		}
	}
}
