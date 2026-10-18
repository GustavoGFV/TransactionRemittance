package com.inter.remittance.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, Client client) {

	private static final int MIN_SECRET_BYTES = 32;

	public SecurityProperties {
		if (jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalArgumentException("app.security.jwt.secret must have at least 32 bytes (HS256)");
		}
	}

	public record Jwt(String secret, Duration ttl) {
	}

	// Credentials of the API consumer allowed to request tokens (users have a password, but it is not used to authenticate API calls yet)
	public record Client(String id, String secret) {
	}

}
