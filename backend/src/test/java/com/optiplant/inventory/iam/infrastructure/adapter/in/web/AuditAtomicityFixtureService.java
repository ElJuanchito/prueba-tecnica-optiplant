package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-source-only fixture exercising {@link AuditWritePort} inside one transaction
 * that then either commits or throws, used solely by {@code AuditAtomicityIT} to
 * prove the write is synchronous — CLAUDE.md's atomic-effects invariant ("los
 * efectos atómicos van por puerto de salida síncrono, nunca por evento"). No
 * production use case wires a mutation to the audit port yet (user/branch admin
 * arrive in slices 5a/5b); design's own testing strategy calls this test
 * "load-bearing" ahead of that, so this fixture exists to make it possible now.
 * Confined to {@code src/test} on purpose, mirroring {@code
 * BranchIsolationFixtureController}'s existing pattern: it must never reach the
 * packaged production JAR.
 */
@Service
class AuditAtomicityFixtureService {

	private final AuditWritePort auditWritePort;
	private final PrincipalAccessor principalAccessor;

	AuditAtomicityFixtureService(AuditWritePort auditWritePort, PrincipalAccessor principalAccessor) {
		this.auditWritePort = auditWritePort;
		this.principalAccessor = principalAccessor;
	}

	@Transactional
	void recordThenMaybeFail(String entityId, boolean shouldFail) {
		AuthenticatedPrincipal principal = principalAccessor.require();
		auditWritePort.record(new AuditEntryCommand(principal.userId(), principal.branchId(), AuditAction.CREATE.name(),
				"audit-atomicity-fixture", entityId, null, null, null));
		if (shouldFail) {
			throw new AtomicityFixtureFailure("Deliberate failure after AuditWritePort.record");
		}
	}

	/** Deliberately unchecked and unmapped by {@link IamExceptionHandler} — the test
	 * only cares that the request does not succeed, not about a specific status. */
	static class AtomicityFixtureFailure extends RuntimeException {
		AtomicityFixtureFailure(String message) {
			super(message);
		}
	}
}
