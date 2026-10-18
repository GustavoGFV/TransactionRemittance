package com.inter.remittance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

class TokenServiceTest {

	private static final String SECRET = "unit-test-secret-with-at-least-32-bytes!!";
	private static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private final Instant now = Instant.now();
	private final TokenService service = new TokenService(
			new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
			new SecurityProperties(new SecurityProperties.Jwt(SECRET, Duration.ofHours(1)),
					new SecurityProperties.Client("client", "secret")),
			Clock.fixed(now, ZoneOffset.UTC));

	@Test
	void issuesSignedTokenForValidCredentials() {
		TokenResponse response = service.issue(new TokenRequest("client", "secret"));

		Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("client");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600);
	}

	@Test
	void rejectsInvalidCredentials() {
		assertThatThrownBy(() -> service.issue(new TokenRequest("client", "wrong")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void rejectsShortJwtSecret() {
		assertThatThrownBy(() -> new SecurityProperties(new SecurityProperties.Jwt("short", Duration.ofHours(1)),
				new SecurityProperties.Client("c", "s"))).isInstanceOf(IllegalArgumentException.class);
	}

}
