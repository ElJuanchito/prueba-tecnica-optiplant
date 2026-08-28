package com.optiplant.inventory.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEntryCommandTest {

	@Test
	void carriesEveryFieldItWasGivenUnchanged() {
		UUID actor = UUID.randomUUID();
		UUID branch = UUID.randomUUID();

		AuditEntryCommand command = new AuditEntryCommand(actor, branch, AuditAction.DISABLE.name(), "users",
				"e0000000-0000-0000-0000-000000000005", "{\"active\":true}", "{\"active\":false}", "127.0.0.1");

		assertThat(command.actorUserId()).isEqualTo(actor);
		assertThat(command.branchId()).isEqualTo(branch);
		assertThat(command.action()).isEqualTo("DISABLE");
		assertThat(command.entityName()).isEqualTo("users");
		assertThat(command.entityId()).isEqualTo("e0000000-0000-0000-0000-000000000005");
		assertThat(command.payloadBefore()).isEqualTo("{\"active\":true}");
		assertThat(command.payloadAfter()).isEqualTo("{\"active\":false}");
		assertThat(command.ipAddress()).isEqualTo("127.0.0.1");
	}

	@Test
	void branchIdMayBeNullForACorporateActorsAction() {
		AuditEntryCommand command = new AuditEntryCommand(UUID.randomUUID(), null, AuditAction.CREATE.name(),
				"branches", "b0000000-0000-0000-0000-000000000004", null, "{\"code\":\"SUC-CTG\"}", null);

		assertThat(command.branchId()).isNull();
	}

	@Test
	void actionAcceptsAFreeFormStringBeyondThisModulesOwnEnum() {
		// audit_logs.action has no CHECK constraint (01-init-schema.sql:428) — a
		// future module must be free to write an action name AuditAction never
		// enumerates (the schema's own examples: 'CREATE_SALE', 'DISPATCH_TRANSFER').
		AuditEntryCommand command = new AuditEntryCommand(UUID.randomUUID(), null, "DISPATCH_TRANSFER", "transfers",
				"3-fixture", null, null, null);

		assertThat(command.action()).isEqualTo("DISPATCH_TRANSFER");
	}
}
