# Branch Administration Specification

## Purpose

CRUD over branches with a unique code, per RF-SEG-03 (`docs/especificacion_requerimientos.md:87`) and CU-SEG-03. CU-SEG-03 has only a catalog row (`docs/casos_de_uso.md:103`); this spec derives its flow from HU-SEG-03's acceptance criteria (`docs/historias_de_usuario.md:90-92`) and RF-SEG-03's text, following CU-SEG-01's structure.

## Requirements

### Requirement: Only ADMIN manages branches

The system MUST restrict branch create, edit, disable, and query-all operations to the `ADMIN` role (`docs/casos_de_uso.md:72`, "Gestionar sucursales y usuarios").

#### Scenario: Non-ADMIN attempts branch management
- GIVEN an authenticated `BRANCH_MANAGER` or `OPERATOR`
- WHEN they call a branch create, edit, or disable endpoint
- THEN the system responds `403 Forbidden`

### Requirement: Branch creation enforces a unique code

The system MUST create a branch with a unique `code`, `name`, and location, and MUST reject creation when the code already exists.

#### Scenario: Successful branch creation
- GIVEN an authenticated `ADMIN` and a `code` not currently in use
- WHEN they create a branch with that code, name, and location
- THEN the branch is persisted, active, and available for user and inventory assignment (HU-SEG-03 acceptance criterion)

#### Scenario: Duplicate branch code
- GIVEN a branch code that already exists
- WHEN an `ADMIN` attempts to create another branch with that code
- THEN the system rejects the operation, indicating the conflict (HU-SEG-03 acceptance criterion)

### Requirement: Branch edit updates name and location, not the identity

The system MUST allow an `ADMIN` to edit a branch's `name`, `address`, `city`, and `phone`, and MUST NOT allow the edit to change the branch's `external_id`.

#### Scenario: Successful branch edit
- GIVEN an existing active branch
- WHEN an `ADMIN` updates its name or location fields
- THEN the changes are persisted and the branch's `external_id` is unchanged

### Requirement: Branch disable is logical, never physical

The system MUST disable a branch by setting `is_active` to false, and MUST NOT delete the branch row. A disabled branch's users MUST be denied login (see authentication capability, CU-SEG-01 EX-02).

#### Scenario: Disabling a branch
- GIVEN an active branch with assigned users
- WHEN an `ADMIN` disables it
- THEN the branch row remains in place with `is_active = false`
- AND its users can no longer authenticate

### Requirement: Branch query lists branches with their status

The system MUST allow an `ADMIN` to query and filter branches, including disabled ones, without exposing the internal numeric `id` (only `external_id`).

#### Scenario: Query includes disabled branches
- GIVEN a mix of active and disabled branches
- WHEN an `ADMIN` queries the branch list
- THEN both active and disabled branches are returned
- AND each branch is identified only by its `external_id`
