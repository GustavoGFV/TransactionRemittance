package com.inter.remittance.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Fixed-window rate limiter keyed by client address, applied only to the endpoints that are public and
 * cheap to abuse: credential endpoints (brute force, sign-up spam) and the quotation lookup.
 * Over the limit the request is answered with 429 and never reaches the controllers.
 * <p>
 * It is intentionally in-memory (Caffeine, already a dependency): it protects each replica without an extra
 * infrastructure component. Behind a reverse proxy the client address is the proxy's unless
 * {@code server.forward-headers-strategy} is configured; a shared limit across replicas would need an API gateway
 * or a distributed store.
 */
public class RateLimitFilter extends OncePerRequestFilter {

	private static final int MAX_TRACKED_CLIENTS = 100_000;

	private final RateLimitProperties properties;
	private final Cache<String, AtomicInteger> counters;

	public RateLimitFilter(RateLimitProperties properties) {
		this(properties, Ticker.systemTicker());
	}

	RateLimitFilter(RateLimitProperties properties, Ticker ticker) {
		this.properties = properties;
		// The entry is created by the first request of the window and expires with it, which resets the counter
		this.counters = Caffeine.newBuilder().expireAfterWrite(properties.window()).maximumSize(MAX_TRACKED_CLIENTS)
				.ticker(ticker).build();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Limit limit = limitFor(request);
		if (limit == null) {
			chain.doFilter(request, response);
			return;
		}
		int used = counters.get(limit.group() + ":" + request.getRemoteAddr(), key -> new AtomicInteger()).incrementAndGet();
		if (used > limit.maxRequests()) {
			reject(response);
			return;
		}
		chain.doFilter(request, response);
	}

	private Limit limitFor(HttpServletRequest request) {
		String path = request.getRequestURI();
		boolean post = HttpMethod.POST.matches(request.getMethod());
		if (post && (path.startsWith("/auth/") || path.equals("/users"))) {
			return new Limit("credentials", properties.authMaxRequests());
		}
		if (HttpMethod.GET.matches(request.getMethod()) && path.startsWith("/quotations")) {
			return new Limit("quotations", properties.quotationsMaxRequests());
		}
		return null;
	}

	private void reject(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(properties.window().toSeconds()));
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
				+ "\"detail\":\"Rate limit exceeded. Try again later.\"}");
	}

	private record Limit(String group, int maxRequests) {
	}

}
