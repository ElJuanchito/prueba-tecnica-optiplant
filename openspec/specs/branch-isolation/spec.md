# Branch Isolation Specification

## Purpose

Confines mutation authority to the caller's own branch while permitting cross-branch reads, per RN-08 (`docs/especificacion_requerimientos.md:193`), RN-14 (`:199`), RNF-SEC-01 (`:235`) and RNF-SEC-03 (`:237`). Sustains HU-SEG-02 (`docs/historias_de_usuario.md:66-79`).

## Requirements

### Requirement: Acting branch is derived from the session only

The system MUST derive the branch context of any operation exclusively from the authenticated session's `branch_id` claim. The system MUST NOT accept a branch identifier from a request body, query parameter, or header as the source of truth for the acting branch (RN-14).

#### Scenario: Client-supplied branch identifier is ignored
- GIVEN an authenticated `OPERATOR` of Branch A
- WHEN a mutation request includes a `branch_id` field pointing to Branch B in its payload
- THEN the system derives the acting branch from the session (Branch A) and ignores the payload value

### Requirement: Mutations are confined to the caller's own branch

The system MUST reject a mutation whose target entity belongs to a branch other than the caller's session branch, unless the caller's role is `ADMIN`.

#### Scenario: Cross-branch mutation is rejected
- GIVEN an authenticated `OPERATOR` or `BRANCH_MANAGER` of Branch A
- WHEN they attempt to mutate a resource belonging to Branch B
- THEN the system responds `403 Forbidden`
- AND no change is persisted (HU-SEG-02 acceptance criterion)

#### Scenario: ADMIN mutates any branch
- GIVEN an authenticated `ADMIN` (session `branch_id` may be `NULL`)
- WHEN they mutate a resource belonging to any branch
- THEN the system authorizes the operation
- AND records it in the audit log (HU-SEG-02 acceptance criterion)

### Requirement: Reads of other branches are permitted, read-only

The system MUST allow `OPERATOR` and `BRANCH_MANAGER` to read data belonging to branches other than their own, and MUST NOT allow that read path to accept any mutating action.

#### Scenario: Cross-branch read succeeds
- GIVEN an authenticated `OPERATOR` of Branch A
- WHEN they query a resource belonging to Branch B
- THEN the system returns the data in read-only form (RN-08)
