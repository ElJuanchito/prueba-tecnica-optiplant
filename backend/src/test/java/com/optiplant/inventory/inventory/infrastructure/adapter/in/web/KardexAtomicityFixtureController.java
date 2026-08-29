package com.optiplant.inventory.inventory.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-source-only fixture used solely by {@code KardexAtomicityIT}. Not matched by any of
 * {@code SecurityConfig}'s specific matchers, so it falls under {@code anyRequest().authenticated()}
 * — the same fallback rule {@code /api/test/audit-atomicity/**} already relies on.
 */
@RestController
@RequestMapping("/api/test/kardex-atomicity")
class KardexAtomicityFixtureController {

	private final KardexAtomicityFixtureService fixtureService;

	KardexAtomicityFixtureController(KardexAtomicityFixtureService fixtureService) {
		this.fixtureService = fixtureService;
	}

	@PostMapping("/{referenceId}")
	ResponseEntity<Void> mutate(@PathVariable String referenceId, @RequestParam UUID productExternalId,
			@RequestParam BigDecimal quantity, @RequestParam boolean shouldFail) {
		fixtureService.mutateThenMaybeFail(referenceId, productExternalId, quantity, shouldFail);
		return ResponseEntity.noContent().build();
	}
}
