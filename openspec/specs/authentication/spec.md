# Authentication Specification

## Purpose

Credential-based login that issues a branch-scoped session, plus logout and inactivity expiry, per RF-SEG-01 (`docs/especificacion_requerimientos.md:85`) and CU-SEG-01 (`docs/casos_de_uso.md:532-555`). Access-token validation stays stateless; only the refresh path touches the database (proposal Decision 1).

## Requirements

### Requirement: Credential login issues an access + refresh token pair

The system MUST authenticate a user by username/password and, on success, MUST issue a short-lived access token (claims: `sub`, `role`, `branch_id`) and a persisted, rotating refresh token. TTLs (access 15 min, refresh inactivity window 8 h) MUST be configuration, not code constants (Product decision P1).

#### Scenario: Successful login
- GIVEN an active user with a valid `branch_id` (or ADMIN with `branch_id = NULL`) and correct credentials
- WHEN the user submits username and password to the login endpoint
- THEN the system returns an access token and a refresh token
- AND the access token carries `sub`, `role`, `branch_id`

#### Scenario: Invalid credentials
- GIVEN a username with no matching active user, or a matching user with a wrong password
- WHEN login is attempted
- THEN the system returns a generic error that does not reveal whether the username exists (CU-SEG-01 EX-01)

#### Scenario: Disabled user or disabled branch
- GIVEN a user whose `is_active` is false, or whose assigned branch has `is_active` false
- WHEN login is attempted with correct credentials
- THEN access is denied (CU-SEG-01 EX-02; `docs/casos_de_uso.md:546,552`)

### Requirement: Access token validation is stateless

The system MUST accept requests bearing a valid, unexpired, unaltered access token without a per-request database or cache lookup, and MUST reject any request with a missing, expired, or altered access token.

#### Scenario: No token
- GIVEN a request to a protected endpoint with no bearer token
- WHEN it is processed
- THEN the system responds `401 Unauthorized` without executing business logic (HU-SEG-01 acceptance criterion)

#### Scenario: Expired or altered token
- GIVEN an access token past its 15-minute TTL, or one whose signature does not verify
- WHEN it is presented
- THEN the request is rejected and the actor must reauthenticate (CU-SEG-01 EX-03)

### Requirement: Refresh token rotation with hashed storage

The system MUST store only a hash of each refresh token (never the raw value, mirroring `users.password_hash`). Presenting a valid, non-expired, non-revoked refresh token MUST issue a new access/refresh pair and MUST revoke the presented token (single use).

#### Scenario: Successful refresh
- GIVEN a valid, not-yet-rotated refresh token within its inactivity window
- WHEN it is presented to the refresh endpoint
- THEN a new access token and a new refresh token are issued
- AND the presented refresh token is revoked

#### Scenario: Reused (already-rotated) refresh token
- GIVEN a refresh token that was already exchanged in a prior rotation
- WHEN it is presented again
- THEN the system rejects it

### Requirement: Refresh inactivity expiry

The system MUST reject a refresh token whose `last_used_at` plus the configured inactivity window has elapsed, even when its absolute expiry is still in the future.

#### Scenario: Idle session past the window
- GIVEN a refresh token last used more than 8 hours ago
- WHEN it is presented
- THEN the system rejects it as expired due to inactivity

### Requirement: Logout revokes only the presenting device's session

The system MUST allow a user to hold several concurrent refresh tokens (multi-device, Product decision P4). Logout MUST revoke only the refresh token presented by the caller, leaving other devices' sessions unaffected.

#### Scenario: Logout on one device
- GIVEN a user with two active refresh tokens (device A, device B)
- WHEN device A calls logout with its refresh token
- THEN device A's refresh token becomes unusable
- AND device B's refresh token remains valid

### Requirement: Login rate limiting

The system MUST throttle repeated failed login attempts from the same identifier using a per-instance, in-memory limiter (RNF-SEC-06; proposal Decision 5).

#### Scenario: Repeated failed attempts throttled
- GIVEN several consecutive failed login attempts for the same identifier within the limiter's window
- WHEN the threshold is exceeded
- THEN further attempts are rejected before credential verification, regardless of correctness
