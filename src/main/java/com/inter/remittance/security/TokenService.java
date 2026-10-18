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

	// Constant-time comparison; non-short-circuit "|" above keeps both checks timing-uniform
	private static boolean matches(String provided, String expected) {
		return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
	}

}
