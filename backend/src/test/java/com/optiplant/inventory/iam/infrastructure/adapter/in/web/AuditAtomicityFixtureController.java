package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-source-only fixture used solely by {@code AuditAtomicityIT}. Not matched by
 * any of {@code SecurityConfig}'s specific matchers, so it falls under {@code
 * anyRequest().authenticated()} — the same fallback rule {@code /api/auth/
 * __protected-probe} and {@code /api/test/branch-fixture/**} already rely on.
 */
@RestController
@RequestMapping("/api/test/audit-atomicity")
class AuditAtomicityFixtureController {

	private final AuditAtomicityFixtureService fixtureService;

	AuditAtomicityFixtureController(AuditAtomicityFixtureService fixtureService) {
		this.fixtureService = fixtureService;
	}

	@PostMapping("/{entityId}")
	ResponseEntity<Void> record(@PathVariable String entityId, @RequestParam boolean shouldFail) {
		fixtureService.recordThenMaybeFail(entityId, shouldFail);
		return ResponseEntity.noContent().build();
	}
}
