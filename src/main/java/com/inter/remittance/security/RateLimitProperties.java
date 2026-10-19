package com.inter.remittance.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Per-client request limits (see {@link RateLimitFilter}). The counters live in the memory of each replica, so the
 * effective limit of a deployment is {@code max-requests x replicas}.
 *
 * @param enabled            turns the limiter on or off
 * @param window             length of the fixed window in which requests are counted
 * @param authMaxRequests    requests per window and client for {@code POST /auth/**} and {@code POST /users}
 *                           (brute force and sign-up abuse)
 * @param quotationsMaxRequests requests per window and client for the public {@code GET /quotations}
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("1m") Duration window,
		@DefaultValue("20") int authMaxRequests,
		@DefaultValue("120") int quotationsMaxRequests) {

	public RateLimitProperties {
		if (window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("app.rate-limit.window must be positive");
		}
		if (authMaxRequests < 1 || quotationsMaxRequests < 1) {
			throw new IllegalArgumentException("app.rate-limit.*-max-requests must be at least 1");
		}
	}

}
