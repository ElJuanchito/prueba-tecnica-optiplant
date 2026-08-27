# User Administration Specification

## Purpose

CRUD over users and their roles, with logical disable only, per RF-SEG-02 (`docs/especificacion_requerimientos.md:86`) and CU-SEG-02. CU-SEG-02 has only a catalog row (`docs/casos_de_uso.md:102`); this spec derives its flow from HU-SEG-03's acceptance criteria (`docs/historias_de_usuario.md:90-95`) and RF-SEG-02's text, following CU-SEG-01's structure.

## Requirements

### Requirement: Only ADMIN manages users

The system MUST restrict user create, edit, disable, and query-all operations to the `ADMIN` role (`docs/casos_de_uso.md:72`).

#### Scenario: Non-ADMIN attempts user management
- GIVEN an authenticated `BRANCH_MANAGER` or `OPERATOR`
- WHEN they call a user create, edit, or disable endpoint
- THEN the system responds `403 Forbidden`

### Requirement: User creation assigns a unique username, unique email, a role, and a branch

The system MUST create a user with a role in `{ADMIN, BRANCH_MANAGER, OPERATOR}` (`users.role` CHECK, `backend/init-db/01-init-schema.sql:38`, no `ROLE_` prefix) and MUST reject creation on a duplicate `username` or `email`. A `BRANCH_MANAGER` or `OPERATOR` MUST be created with a `branch_id`; only `ADMIN` MAY be created with `branch_id = NULL` (corporate scope, `backend/init-db/01-init-schema.sql:33`).

#### Scenario: Successful user creation
- GIVEN an authenticated `ADMIN`, a unique username and email, and a valid role/branch combination
- WHEN they create the user
- THEN the user is persisted, active, with a BCrypt-hashed password

#### Scenario: Duplicate username
- GIVEN a username already in use by another user
- WHEN an `ADMIN` attempts to create a user with that username
- THEN the system rejects the operation, indicating the conflict

#### Scenario: Duplicate email
- GIVEN an email already in use by another user
- WHEN an `ADMIN` attempts to create a user with that email
- THEN the system rejects the operation, indicating the conflict

#### Scenario: Non-ADMIN role without a branch
- GIVEN a request to create a `BRANCH_MANAGER` or `OPERATOR` with no `branch_id`
- WHEN the creation is submitted
- THEN the system rejects it as invalid

### Requirement: User edit updates role, branch, and profile fields

The system MUST allow an `ADMIN` to edit an existing user's role, branch assignment, full name, and email (subject to the same uniqueness and role/branch rules as creation), and MUST NOT allow the edit to change the user's `external_id`.

#### Scenario: A user's role changes
- GIVEN an active `OPERATOR`
- WHEN an `ADMIN` edits their role to `BRANCH_MANAGER`
- THEN the change is persisted
- AND subsequent logins reflect the new role's authorized capabilities (HU-SEG-03 acceptance criterion)

### Requirement: User disable is logical and revokes active sessions

The system MUST disable a user by setting `is_active` to false, and MUST NOT delete the user row or any of their historical movements. Disabling MUST immediately revoke every one of that user's refresh tokens (Product decision P2, P4); a live access token remains valid until its own expiry.

#### Scenario: Disabling a user
- GIVEN an active user holding two concurrent refresh tokens
- WHEN an `ADMIN` disables the user
- THEN the user row remains with `is_active = false`
- AND both refresh tokens are revoked
- AND the user's historical movements remain visible and intact (HU-SEG-03 acceptance criterion)

#### Scenario: Disabled user attempts login
- GIVEN a disabled user
- WHEN they attempt to authenticate with correct credentials
- THEN access is denied (see authentication capability, CU-SEG-01 EX-02)

### Requirement: User query lists users without exposing internal identifiers

The system MUST allow an `ADMIN` to query and filter users, including disabled ones, exposing only `external_id`, never the internal numeric `id`.

#### Scenario: Query includes disabled users
- GIVEN a mix of active and disabled users
- WHEN an `ADMIN` queries the user list
- THEN both are returned, each identified only by `external_id`
