package com.optiplant.inventory.catalog.application.service;

import com.optiplant.inventory.catalog.application.port.in.ManageCategoriesUseCase;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.CategoryFilter;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.CategoryPage;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.CategoryUpdate;
import com.optiplant.inventory.catalog.application.port.out.CategoryRepositoryPort.NewCategory;
import com.optiplant.inventory.catalog.domain.exception.CategoryInUseException;
import com.optiplant.inventory.catalog.domain.exception.CategoryNotFoundException;
import com.optiplant.inventory.catalog.domain.exception.DuplicateCategoryNameException;
import com.optiplant.inventory.catalog.domain.model.Category;
import com.optiplant.inventory.catalog.domain.model.CategoryName;
import com.optiplant.inventory.catalog.domain.model.CategorySummary;
import com.optiplant.inventory.shared.audit.AuditAction;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates category administration (CU-INV-01): list, get, create, edit,
 * disable, enable. Mirrors {@code iam}'s {@code BranchAdminService} — every
 * mutation is one {@code @Transactional} that ends with an {@link AuditWritePort}
 * write inside the same transaction (R-15, CLAUDE.md's synchronous-effects
 * invariant; never {@code @Async} or {@code AFTER_COMMIT}).
 *
 * <p>The catalog is corporate master data with no branch dimension, so every
 * audit entry carries {@code branchId = null} and {@code entityName =
 * "categories"} (R-15, R-16). Reads take no actor and are {@code readOnly}.
 */
@Service
public class CategoryAdminService implements ManageCategoriesUseCase {

	private final CategoryRepositoryPort categoryRepository;
	private final AuditWritePort auditWritePort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public CategoryAdminService(CategoryRepositoryPort categoryRepository, AuditWritePort auditWritePort) {
		this.categoryRepository = categoryRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional(readOnly = true)
	public CategoryPage list(CategoryQuery query) {
		return categoryRepository.list(new CategoryFilter(query.name(), query.active(), query.page(), query.size()));
	}

	@Override
	@Transactional(readOnly = true)
	public CategorySummary get(UUID externalId) {
		return categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CategoryNotFoundException(externalId));
	}

	@Override
	@Transactional
	public CategorySummary create(AuthenticatedPrincipal actor, CreateCategoryCommand command) {
		CategoryName name = new CategoryName(command.name());
		requireUniqueName(name, null);

		CategorySummary created = categoryRepository.create(new NewCategory(name.value(), command.description()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.CREATE.name(), "categories",
				created.category().externalId().toString(), null, serializePayload(created.category()), null));
		return created;
	}

	@Override
	@Transactional
	public CategorySummary edit(AuthenticatedPrincipal actor, UUID externalId, EditCategoryCommand command) {
		CategorySummary existing = categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CategoryNotFoundException(externalId));

		CategoryName name = new CategoryName(command.name());
		requireUniqueName(name, externalId);

		CategorySummary updated = categoryRepository.update(externalId,
				new CategoryUpdate(name.value(), command.description(), Instant.now()));

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.UPDATE.name(), "categories",
				externalId.toString(), serializePayload(existing.category()), serializePayload(updated.category()),
				null));
		return updated;
	}

	@Override
	@Transactional
	public CategorySummary disable(AuthenticatedPrincipal actor, UUID externalId) {
		CategorySummary existing = categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CategoryNotFoundException(externalId));

		if (!existing.category().active()) {
			return existing; // idempotent — already disabled, nothing to mutate or audit (R-03)
		}
		if (categoryRepository.hasActiveProducts(externalId)) {
			throw new CategoryInUseException("category " + externalId + " still has active products");
		}

		CategorySummary updated = categoryRepository.setActive(externalId, false, Instant.now());

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.DISABLE.name(), "categories",
				externalId.toString(), serializePayload(existing.category()), serializePayload(updated.category()),
				null));
		return updated;
	}

	@Override
	@Transactional
	public CategorySummary enable(AuthenticatedPrincipal actor, UUID externalId) {
		CategorySummary existing = categoryRepository.findByExternalId(externalId)
				.orElseThrow(() -> new CategoryNotFoundException(externalId));

		if (existing.category().active()) {
			return existing; // idempotent — already active, nothing to mutate or audit (R-03)
		}

		CategorySummary updated = categoryRepository.setActive(externalId, true, Instant.now());

		auditWritePort.record(new AuditEntryCommand(actor.userId(), null, AuditAction.ENABLE.name(), "categories",
				externalId.toString(), serializePayload(existing.category()), serializePayload(updated.category()),
				null));
		return updated;
	}

	private void requireUniqueName(CategoryName name, UUID excludingExternalId) {
		if (categoryRepository.existsByNameIgnoringCase(name.comparisonKey(), excludingExternalId)) {
			throw new DuplicateCategoryNameException("category name '" + name.value() + "' is already in use");
		}
	}

	private String serializePayload(Category category) {
		if (category == null) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(CategoryAuditPayload.from(category));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record CategoryAuditPayload(String name, String description, boolean active) {
		static CategoryAuditPayload from(Category category) {
			return new CategoryAuditPayload(category.name().value(), category.description(), category.active());
		}
	}
}
