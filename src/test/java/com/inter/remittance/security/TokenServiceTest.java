package com.inter.remittance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.inter.remittance.user.model.User;
import com.inter.remittance.user.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class TokenServiceTest {

	private static final String SECRET = "unit-test-secret-with-at-least-32-bytes!!";
	private static final SecretKeySpec KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

	private final Instant now = Instant.now();
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private final TokenService service = new TokenService(
			new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
			new SecurityProperties(new SecurityProperties.Jwt(SECRET, Duration.ofHours(1)),
					new SecurityProperties.Client("client", "secret")),
			Clock.fixed(now, ZoneOffset.UTC), users, passwordEncoder);

	@Test
	void issuesSignedTokenForValidCredentials() {
		TokenResponse response = service.issue(new TokenRequest("client", "secret"));

		Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo("client");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.expiresIn()).isEqualTo(3600);
	}

	@Test
	void rejectsInvalidCredentials() {
		assertThatThrownBy(() -> service.issue(new TokenRequest("client", "wrong")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void loginIssuesUserTokenBoundToTheUserId() {
		User user = new User("A", "a@mail.com", "52998224725", passwordEncoder.encode("S3nh@Forte2026"), BigDecimal.ZERO, BigDecimal.ZERO);
		when(users.findByEmail("a@mail.com")).thenReturn(Optional.of(user));

		TokenResponse response = service.login(new LoginRequest(" A@Mail.com ", "S3nh@Forte2026"));

		Jwt jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(response.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(user.getPublicId().toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
	}

	@Test
	void loginRejectsWrongPasswordAndUnknownEmail() {
		User user = new User("A", "a@mail.com", "52998224725", passwordEncoder.encode("S3nh@Forte2026"), BigDecimal.ZERO, BigDecimal.ZERO);
		when(users.findByEmail("a@mail.com")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.login(new LoginRequest("a@mail.com", "wrong"))).isInstanceOf(InvalidCredentialsException.class);
		assertThatThrownBy(() -> service.login(new LoginRequest("x@mail.com", "S3nh@Forte2026"))).isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void rejectsShortJwtSecret() {
		assertThatThrownBy(() -> new SecurityProperties(new SecurityProperties.Jwt("short", Duration.ofHours(1)),
				new SecurityProperties.Client("c", "s"))).isInstanceOf(IllegalArgumentException.class);
	}

}
