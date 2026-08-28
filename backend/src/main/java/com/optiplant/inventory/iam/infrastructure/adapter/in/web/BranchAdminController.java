package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase;
import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.BranchQuery;
import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.CreateBranchCommand;
import com.optiplant.inventory.iam.application.port.in.ManageBranchesUseCase.EditBranchCommand;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort.BranchPage;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/admin/branches/**} — branch create/edit/disable/query
 * (branch-administration capability). {@code ADMIN}-gated by {@code
 * SecurityConfig}'s matcher (slice 3); no service-level role check is needed
 * here (mirrors {@code UserAdminController}). Every response DTO exposes
 * {@code external_id} only, never the internal numeric {@code id}.
 */
@RestController
@RequestMapping("/api/admin/branches")
public class BranchAdminController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageBranchesUseCase manageBranchesUseCase;
	private final PrincipalAccessor principalAccessor;

	public BranchAdminController(ManageBranchesUseCase manageBranchesUseCase, PrincipalAccessor principalAccessor) {
		this.manageBranchesUseCase = manageBranchesUseCase;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public BranchResponse create(@Valid @RequestBody CreateBranchRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		BranchProfile created = manageBranchesUseCase.create(actor, new CreateBranchCommand(request.code(),
				request.name(), request.address(), request.city(), request.phone()));
		return toResponse(created);
	}

	@PutMapping("/{externalId}")
	public BranchResponse edit(@PathVariable UUID externalId, @Valid @RequestBody EditBranchRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		BranchProfile updated = manageBranchesUseCase.edit(actor, externalId,
				new EditBranchCommand(request.name(), request.address(), request.city(), request.phone()));
		return toResponse(updated);
	}

	@PatchMapping("/{externalId}/disable")
	public ResponseEntity<Void> disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		manageBranchesUseCase.disable(actor, externalId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	public BranchPageResponse list(@RequestParam(required = false) Boolean active,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		BranchPage result = manageBranchesUseCase.list(new BranchQuery(active, page, pageSize));

		List<BranchResponse> content = result.content().stream().map(BranchAdminController::toResponse).toList();
		return new BranchPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	private static BranchResponse toResponse(BranchProfile branch) {
		return new BranchResponse(branch.externalId(), branch.code(), branch.name(), branch.address(),
				branch.city(), branch.phone(), branch.active());
	}

	public record CreateBranchRequest(@NotBlank String code, @NotBlank String name, @NotBlank String address,
			@NotBlank String city, String phone) {
	}

	/** {@code code} is absent — edit never changes it. */
	public record EditBranchRequest(@NotBlank String name, @NotBlank String address, @NotBlank String city,
			String phone) {
	}

	public record BranchResponse(UUID externalId, String code, String name, String address, String city,
			String phone, boolean active) {
	}

	public record BranchPageResponse(List<BranchResponse> content, long totalElements, int page, int size) {
	}
}
