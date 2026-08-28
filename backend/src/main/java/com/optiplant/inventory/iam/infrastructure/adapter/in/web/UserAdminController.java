package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase;
import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.CreateUserCommand;
import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.EditUserCommand;
import com.optiplant.inventory.iam.application.port.in.ManageUsersUseCase.UserQuery;
import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort;
import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import com.optiplant.inventory.shared.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
 * {@code /api/admin/users/**} — user create/edit/disable/query
 * (user-administration capability). {@code SecurityConfig}'s matcher admits
 * {@code ADMIN} and {@code BRANCH_MANAGER}; {@code OPERATOR} never reaches
 * this controller (mirrors {@code AuditLogController}'s equivalent
 * rationale). {@code BRANCH_MANAGER}'s scope (only {@code OPERATOR}, only
 * their own branch) is enforced in {@code UserAdminService}, not here — the
 * actor is passed through unchanged so the service can apply it. Every
 * response DTO exposes {@code external_id} only, never the internal numeric
 * {@code id} or the password hash.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageUsersUseCase manageUsersUseCase;
	private final BranchRepositoryPort branchRepository;
	private final PrincipalAccessor principalAccessor;

	public UserAdminController(ManageUsersUseCase manageUsersUseCase, BranchRepositoryPort branchRepository,
			PrincipalAccessor principalAccessor) {
		this.manageUsersUseCase = manageUsersUseCase;
		this.branchRepository = branchRepository;
		this.principalAccessor = principalAccessor;
	}

	@PostMapping
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		UserAccount created = manageUsersUseCase.create(actor, new CreateUserCommand(request.username(),
				request.email(), request.password(), request.fullName(), request.role(), request.branchId()));
		return toResponse(created);
	}

	@PutMapping("/{externalId}")
	public UserResponse edit(@PathVariable UUID externalId, @Valid @RequestBody EditUserRequest request) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		UserAccount updated = manageUsersUseCase.edit(actor, externalId,
				new EditUserCommand(request.email(), request.fullName(), request.role(), request.branchId()));
		return toResponse(updated);
	}

	@PatchMapping("/{externalId}/disable")
	public ResponseEntity<Void> disable(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		manageUsersUseCase.disable(actor, externalId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	public UserPageResponse list(@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Role role, @RequestParam(required = false) UUID branchId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		UserPage result = manageUsersUseCase.list(actor, new UserQuery(active, role, branchId, page, pageSize));

		List<UserResponse> content = result.content().stream().map(this::toResponse).toList();
		return new UserPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	private UserResponse toResponse(UserAccount account) {
		String branchName = null;
		String branchCode = null;
		if (account.branchExternalId() != null) {
			BranchProfile branch = branchRepository.findByExternalId(account.branchExternalId()).orElse(null);
			if (branch != null) {
				branchName = branch.name();
				branchCode = branch.code();
			}
		}
		return new UserResponse(account.externalId(), account.username(), account.email(), account.fullName(),
				account.role(), account.branchExternalId(), branchName, branchCode, account.active());
	}

	public record CreateUserRequest(@NotBlank String username, @NotBlank @Email String email,
			@NotBlank @Size(min = 8) String password, @NotBlank String fullName, @NotNull Role role, UUID branchId) {
	}

	/** {@code username} is absent — edit never changes it. */
	public record EditUserRequest(@NotBlank @Email String email, @NotBlank String fullName, @NotNull Role role,
			UUID branchId) {
	}

	public record UserResponse(UUID externalId, String username, String email, String fullName, Role role,
			UUID branchId, String branchName, String branchCode, boolean active) {
	}

	public record UserPageResponse(List<UserResponse> content, long totalElements, int page, int size) {
	}
}
