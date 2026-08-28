# Audit Log Specification

## Purpose

Synchronous, transactional write of mutation events plus a filtered query, per RF-SEG-04 (`docs/especificacion_requerimientos.md:88`), RNF-SEC-08 (`:242`) and CU-SEG-04. CU-SEG-04 has only a catalog row (`docs/casos_de_uso.md:104`); this spec derives its flow from RF-SEG-04's text and the RBAC matrix (`docs/casos_de_uso.md:91`), following CU-SEG-01's structure. Reads are never audited (Product decision P3).

## Requirements

### Requirement: Every mutation writes an audit entry in the same transaction

The system MUST record `user_id`, `branch_id`, `action`, `entity_name`, `entity_id`, `payload_before`, `payload_after`, and `ip_address` for each mutation, through a synchronous output port invoked inside the caller's own transaction (CLAUDE.md: atomic effects use a synchronous port, never an event). If the audit write fails, the triggering mutation MUST NOT be persisted.

#### Scenario: Mutation succeeds and is audited
- GIVEN an authenticated `ADMIN` disabling a user
- WHEN the disable operation commits
- THEN an audit entry exists for that action, user, and branch in the same transaction

#### Scenario: Audit write failure aborts the mutation
- GIVEN a mutation whose audit write fails
- WHEN the operation is attempted
- THEN the mutation is rolled back and no state change is persisted

### Requirement: Reads are never audited

The system MUST NOT create an audit entry for a read-only operation, including cross-branch reads (Product decision P3).

#### Scenario: A read produces no audit entry
- GIVEN an authenticated `OPERATOR` reading inventory of another branch
- WHEN the read completes
- THEN no audit entry is created for it

### Requirement: Audit query is filtered and role-scoped

The system MUST allow filtering the audit log by user, branch, entity, action, and date range (RF-SEG-04). `ADMIN` MUST see entries across all branches; `BRANCH_MANAGER` MUST see only entries for their own branch; `OPERATOR` MUST be denied access (`docs/casos_de_uso.md:91`).

#### Scenario: ADMIN queries across branches
- GIVEN audit entries from multiple branches
- WHEN an `ADMIN` queries with a date-range filter and no branch filter
- THEN entries from all branches within that range are returned

#### Scenario: BRANCH_MANAGER is scoped to their own branch
- GIVEN a `BRANCH_MANAGER` of Branch A
- WHEN they query the audit log
- THEN only entries whose `branch_id` is Branch A are returned, regardless of any branch filter they submit

#### Scenario: OPERATOR is denied
- GIVEN an authenticated `OPERATOR`
- WHEN they call the audit query endpoint
- THEN the system responds `403 Forbidden`

### Requirement: Audit query results are paginated

The system MUST paginate audit log query results with a maximum page size, since the collection is potentially unbounded (RNF-PER-04).

#### Scenario: Large result set is paginated
- GIVEN an audit log filter matching more entries than the maximum page size
- WHEN the query is executed without an explicit page size
- THEN the response returns at most the maximum page size and indicates further pages exist

### Requirement: Audit entries are immutable and retained

The system MUST NOT expose any endpoint or mechanism to edit or physically delete an audit entry (RNF-SEC-08, RNF-INT-02).

#### Scenario: No mutation path exists for audit entries
- GIVEN a persisted audit entry
- WHEN the API surface is inspected
- THEN no endpoint accepts an update or delete for audit entries
