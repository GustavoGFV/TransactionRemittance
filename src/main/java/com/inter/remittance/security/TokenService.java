package com.inter.remittance.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues a short-lived JWT in exchange for valid API client credentials.
 * 
 * There is no end-user login in this service, so the token only proves the caller is a trusted client.
 * It is deliberately not bound to a user id.
 */
@Service
public class TokenService {

	private static final String ISSUER = "inter-remittance";

	private final JwtEncoder encoder;
	private final SecurityProperties properties;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public TokenResponse issue(TokenRequest request) {
		if (!matches(request.clientId(), properties.client().id()) | !matches(request.clientSecret(), properties.client().secret())) {
			throw new InvalidCredentialsException();
		}
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(request.clientId())
				.issuedAt(now)
				.expiresAt(now.plus(properties.jwt().ttl()))
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		return new TokenResponse(token, "Bearer", properties.jwt().ttl().toSeconds());
	}

	// Both checks always run and the comparison is constant-time; otherwise the response time
	// would reveal whether the clientId or the secret was the wrong one.
	private static boolean matches(String provided, String expected) {
		return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
	}

}
