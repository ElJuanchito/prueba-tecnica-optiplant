package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.domain.service.BranchAccessPolicy;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Test-source-only fixture used solely by {@code BranchIsolationIT} to exercise
 * {@link BranchAccessPolicy} end to end through the real security filter chain.
 *
 * <p><b>Flag (tasks.md 3.4)</b>: no production business module owns a
 * branch-scoped mutable resource yet — those arrive in slices 4-5 (audit,
 * user/branch admin). Branch isolation itself, per RN-08/RN-14, is a
 * cross-cutting policy that must exist and be provably enforced before any
 * concrete resource needs it, so this slice tests the policy through a
 * throwaway endpoint rather than waiting for a future slice to prove it
 * incidentally. Confined to {@code src/test} on purpose: it must never reach
 * the packaged production JAR.
 *
 * <p>Deliberately takes no branch identifier from the client at all — only an
 * opaque {@code resource} name that this class maps, server-side, to one of
 * the three seeded branches ({@code backend/init-db/02-seed-data.sql}). That
 * sidesteps any ambiguity with branch-isolation's "acting branch is derived
 * from the session only" requirement: there is no {@code branch_id}-shaped
 * parameter anywhere in this endpoint's contract to accidentally trust.
 */
@RestController
@RequestMapping("/api/test/branch-fixture")
class BranchIsolationFixtureController {

	private static final Map<String, UUID> RESOURCE_BRANCH = Map.of("bogota",
			UUID.fromString("b0000000-0000-0000-0000-000000000001"), "medellin",
			UUID.fromString("b0000000-0000-0000-0000-000000000002"), "cali",
			UUID.fromString("b0000000-0000-0000-0000-000000000003"));

	private final PrincipalAccessor principalAccessor;
	private final BranchAccessPolicy branchAccessPolicy = new BranchAccessPolicy();

	BranchIsolationFixtureController(PrincipalAccessor principalAccessor) {
		this.principalAccessor = principalAccessor;
	}

	/** Cross-branch reads are always permitted (branch-isolation "Reads of other
	 * branches are permitted, read-only") — no policy check at all. */
	@GetMapping("/{resource}")
	ResponseEntity<Void> read(@PathVariable String resource) {
		branchOf(resource);
		return ResponseEntity.ok().build();
	}

	/** Mutations go through {@link BranchAccessPolicy}; a rejection is mapped to
	 * {@code 403} by the production {@code IamExceptionHandler} — this class
	 * throws nothing HTTP-specific itself. */
	@PatchMapping("/{resource}")
	ResponseEntity<Void> mutate(@PathVariable String resource) {
		UUID branchId = branchOf(resource);
		branchAccessPolicy.requireMayMutate(principalAccessor.require(), branchId);
		return ResponseEntity.noContent().build();
	}

	private UUID branchOf(String resource) {
		UUID branchId = RESOURCE_BRANCH.get(resource);
		if (branchId == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown fixture resource: " + resource);
		}
		return branchId;
	}
}
