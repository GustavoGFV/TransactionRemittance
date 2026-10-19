package com.inter.remittance.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/*
 * Index of the rate limiter scenarios (no Spring context, no Docker; time is controlled by a fake ticker):
 *  1. Requests up to the limit pass; the next one in the same window is answered with 429 and never reaches the chain.
 *  2. The 429 is an RFC 7807 problem+json body with a Retry-After header.
 *  3. The counter is per client address and per endpoint group (credentials x quotations).
 *  4. The window expires and the client can call again.
 *  5. Endpoints that are not rate limited (health, remittances, other HTTP methods) are never blocked.
 */
class RateLimitFilterTest {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final AtomicLong nanos = new AtomicLong();
	private RateLimitFilter filter;

	@BeforeEach
	void setUp() {
		filter = new RateLimitFilter(new RateLimitProperties(true, WINDOW, 3, 2), nanos::get);
	}

	@Test
	void blocksTheRequestAfterTheLimitWithProblemJson() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(200);
		}

		Result blocked = call("POST", "/auth/login", "10.0.0.1");

		assertThat(blocked.status).isEqualTo(429);
		assertThat(blocked.reachedController).isFalse();
		assertThat(blocked.contentType).startsWith("application/problem+json");
		assertThat(blocked.retryAfter).isEqualTo("60");
		assertThat(blocked.body).contains("\"status\":429").contains("Too Many Requests");
	}

	@Test
	void credentialEndpointsShareTheSameBucket() throws Exception {
		call("POST", "/auth/login", "10.0.0.1");
		call("POST", "/auth/token", "10.0.0.1");
		call("POST", "/users", "10.0.0.1");

		assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(429);
	}

	@Test
	void eachClientAddressHasItsOwnCounter() throws Exception {
		for (int i = 0; i < 3; i++) {
			call("POST", "/auth/login", "10.0.0.1");
		}

		assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(429);
		assertThat(call("POST", "/auth/login", "10.0.0.2").status).isEqualTo(200);
	}

	@Test
	void quotationsHaveTheirOwnLimitAndBucket() throws Exception {
		assertThat(call("GET", "/quotations", "10.0.0.1").status).isEqualTo(200);
		assertThat(call("GET", "/quotations", "10.0.0.1").status).isEqualTo(200);
		assertThat(call("GET", "/quotations", "10.0.0.1").status).isEqualTo(429);
		assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(200);
	}

	@Test
	void theWindowExpiresAndTheClientCanCallAgain() throws Exception {
		for (int i = 0; i < 3; i++) {
			call("POST", "/auth/login", "10.0.0.1");
		}
		assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(429);

		nanos.addAndGet(WINDOW.plusSeconds(1).toNanos());

		assertThat(call("POST", "/auth/login", "10.0.0.1").status).isEqualTo(200);
	}

	@Test
	void otherEndpointsAndMethodsAreNeverLimited() throws Exception {
		for (int i = 0; i < 50; i++) {
			assertThat(call("GET", "/actuator/health", "10.0.0.1").status).isEqualTo(200);
			assertThat(call("POST", "/remittances/buy", "10.0.0.1").status).isEqualTo(200);
			assertThat(call("GET", "/users", "10.0.0.1").status).isEqualTo(200);
			assertThat(call("GET", "/auth/login", "10.0.0.1").status).isEqualTo(200);
			assertThat(call("POST", "/quotations", "10.0.0.1").status).isEqualTo(200);
		}
	}

	@Test
	void rejectsInvalidConfiguration() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new RateLimitProperties(true, Duration.ZERO, 3, 2));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new RateLimitProperties(true, WINDOW, 0, 2));
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new RateLimitProperties(true, WINDOW, 3, 0));
	}

	private Result call(String method, String path, String address) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setRemoteAddr(address);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(request, response, chain);
		boolean reached = chain.getRequest() != null;
		return new Result(response.getStatus(), reached, response.getContentType(),
				response.getHeader("Retry-After"), response.getContentAsString());
	}

	private record Result(int status, boolean reachedController, String contentType, String retryAfter, String body) {
	}

}
