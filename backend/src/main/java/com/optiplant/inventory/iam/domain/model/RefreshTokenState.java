package com.optiplant.inventory.iam.domain.model;

/**
 * Outcome of evaluating a {@link RefreshTokenGrant} against "now" and the configured
 * idle window (design's REFRESH data flow). Pure classification, no side effects —
 * the caller ({@code SessionRefreshService}) decides what a {@code REUSE_DETECTED}
 * outcome does (revoke the whole family).
 */
public enum RefreshTokenState {
	VALID, REUSE_DETECTED, EXPIRED, IDLE_EXPIRED
}
