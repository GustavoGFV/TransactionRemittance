package com.inter.remittance.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;

/**
 * Issues short-lived JWTs with a role claim:
 * <ul>
 * <li>ADMIN: API client credentials; manages users, never moves money.</li>
 * <li>USER: e-mail and password of a registered user; the subject is the user's public id, which the
 * remittance endpoints compare with the id in the request.</li>
 * </ul>
 */
@Service
public class TokenService {

	public static final String ROLES_CLAIM = "roles";
	public static final String ROLE_ADMIN = "ADMIN";
	public static final String ROLE_USER = "USER";

	private static final String ISSUER = "inter-remittance";

	private final JwtEncoder encoder;
	private final SecurityProperties properties;
	private final Clock clock;
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	// Spends the same time on unknown e-mails as on wrong passwords
	private final String dummyHash;

	public TokenService(JwtEncoder encoder, SecurityProperties properties, Clock clock, UserRepository users,
			PasswordEncoder passwordEncoder) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.dummyHash = passwordEncoder.encode("timing-equalizer");
	}

	public TokenResponse issue(TokenRequest request) {
		if (!matches(request.clientId(), properties.client().id()) | !matches(request.clientSecret(), properties.client().secret())) {
			throw new InvalidCredentialsException();
		}
		return sign(request.clientId(), ROLE_ADMIN);
	}

	public TokenResponse login(LoginRequest request) {
		User user = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
		if (user == null || !matches) {
			throw new InvalidCredentialsException();
		}
		return sign(user.getPublicId().toString(), ROLE_USER);
	}

	private TokenResponse sign(String subject, String role) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(subject)
				.claim(ROLES_CLAIM, List.of(role))
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
