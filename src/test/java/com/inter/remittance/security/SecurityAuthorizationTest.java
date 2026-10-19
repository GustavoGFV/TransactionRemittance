package com.inter.remittance.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.inter.remittance.quotation.controller.QuotationController;
import com.inter.remittance.quotation.service.QuotationService;
import com.inter.remittance.remittance.controller.RemittanceController;
import com.inter.remittance.remittance.service.RemittanceService;
import com.inter.remittance.user.controller.UserController;
import com.inter.remittance.user.dto.UserResponse;
import com.inter.remittance.user.mapper.UserMapper;
import com.inter.remittance.user.model.DocumentType;
import com.inter.remittance.user.service.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/*
 * Index of the authentication and authorization scenarios (real security filter chain and real JWT decoder,
 * mocked services; no database and no Docker):
 *  1. Authentication - missing, malformed, wrongly signed, expired and tampered tokens are rejected with 401.
 *  2. Public routes  - sign-up, login, token issuing and quotations do not need a token.
 *  3. Remittances    - only the USER who owns the userId (path or body) is allowed; other users and ADMIN get 403.
 *  4. Users          - ADMIN manages anyone; a USER reads, updates and deletes only itself and cannot list users.
 *  5. Roles          - a valid token with an unknown role is rejected with 403.
 * @PreAuthorize runs after argument binding, so the request bodies used here are valid.
 */
@WebMvcTest(controllers = { RemittanceController.class, UserController.class, AuthController.class, QuotationController.class })
@Import(SecurityConfig.class)
class SecurityAuthorizationTest {

	private static final String ME = "3f2b8c1e-6a4d-4f7e-9b1a-2c5d7e8f9a0b";
	private static final String SOMEONE_ELSE = "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JwtEncoder encoder;

	@MockitoBean
	private RemittanceService remittanceService;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserMapper userMapper;

	@MockitoBean
	private TokenService tokenService;

	@MockitoBean
	private QuotationService quotationService;

	@BeforeEach
	void stubServices() {
		when(remittanceService.balance(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(remittanceService.preview(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
		when(remittanceService.buy(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(remittanceService.reverse(any())).thenReturn(CompletableFuture.completedFuture(null));
		when(quotationService.findBuyQuotations(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
		when(userService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of()));
		when(userMapper.toResponse(any())).thenReturn(new UserResponse(UUID.fromString(ME), "Maria Silva",
				"maria@mail.com", "52998224725", DocumentType.CPF, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), Instant.now()));
		when(tokenService.login(any())).thenReturn(new TokenResponse("token", "Bearer", 3600));
		when(tokenService.issue(any())).thenReturn(new TokenResponse("token", "Bearer", 3600));
	}

	// ---- 1. Authentication -------------------------------------------------------------------------------

	@Test
	void requestsWithoutTokenAreRejectedWith401() throws Exception {
		for (MockHttpServletRequestBuilder request : protectedRequests()) {
			mvc.perform(request).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void malformedTokenIsRejectedWith401() throws Exception {
		mvc.perform(get("/users").headers(bearer("not-a-jwt"))).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejectedWith401() throws Exception {
		JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(
				new SecretKeySpec("another-secret-key-with-32-bytes!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));

		mvc.perform(get("/users").headers(bearer(sign(attacker, "attacker", "ADMIN", Instant.now().plusSeconds(3600)))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsRejectedWith401() throws Exception {
		String expired = sign(encoder, "admin-client", "ADMIN", Instant.now().minus(10, ChronoUnit.MINUTES));

		mvc.perform(get("/users").headers(bearer(expired))).andExpect(status().isUnauthorized());
	}

	@Test
	void tokenWithTamperedRoleIsRejectedWith401() throws Exception {
		String[] parts = token(ME, "USER").split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).replace("USER", "ADMIN");
		String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

		mvc.perform(get("/users").headers(bearer(forged))).andExpect(status().isUnauthorized());
	}

	// ---- 2. Public routes --------------------------------------------------------------------------------

	@Test
	void signUpLoginTokenAndQuotationsDoNotNeedAToken() throws Exception {
		mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(
				"{\"name\":\"Maria Silva\",\"email\":\"maria@mail.com\",\"document\":\"529.982.247-25\",\"password\":\"S3nh@Forte2026\"}"))
				.andExpect(status().isCreated());
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"maria@mail.com\",\"password\":\"S3nh@Forte2026\"}")).andExpect(status().isOk());
		mvc.perform(post("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.content("{\"clientId\":\"remittance-client\",\"clientSecret\":\"remittance-secret\"}")).andExpect(status().isOk());
		mvc.perform(get("/quotations").param("date", "'10-01-2026'")).andExpect(request().asyncStarted());
	}

	// ---- 3. Remittances: ownership -----------------------------------------------------------------------

	@Test
	void userOperatesOnItsOwnRemittances() throws Exception {
		HttpHeaders token = bearer(token(ME, "USER"));

		mvc.perform(get("/remittances/users/{id}/balance", ME).headers(token)).andExpect(request().asyncStarted());
		mvc.perform(get("/remittances/users/{id}/preview", ME).param("usdAmount", "10.00").headers(token))
				.andExpect(request().asyncStarted());
		mvc.perform(buy(ME).headers(token)).andExpect(request().asyncStarted());
		mvc.perform(reverse(ME).headers(token)).andExpect(request().asyncStarted());
	}

	@Test
	void userCannotOperateOnAnotherUsersRemittances() throws Exception {
		HttpHeaders token = bearer(token(ME, "USER"));

		mvc.perform(get("/remittances/users/{id}/balance", SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
		mvc.perform(get("/remittances/users/{id}/preview", SOMEONE_ELSE).param("usdAmount", "10.00").headers(token))
				.andExpect(status().isForbidden());
		mvc.perform(buy(SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
		mvc.perform(reverse(SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
	}

	@Test
	void adminCannotOperateRemittances() throws Exception {
		HttpHeaders token = bearer(token("admin-client", "ADMIN"));

		mvc.perform(get("/remittances/users/{id}/balance", ME).headers(token)).andExpect(status().isForbidden());
		mvc.perform(buy(ME).headers(token)).andExpect(status().isForbidden());
		mvc.perform(reverse(ME).headers(token)).andExpect(status().isForbidden());
	}

	// ---- 4. Users: roles and ownership -------------------------------------------------------------------

	@Test
	void adminManagesAnyUser() throws Exception {
		HttpHeaders token = bearer(token("admin-client", "ADMIN"));

		mvc.perform(get("/users").headers(token)).andExpect(status().isOk());
		mvc.perform(get("/users/{id}", SOMEONE_ELSE).headers(token)).andExpect(status().isOk());
		mvc.perform(update(SOMEONE_ELSE).headers(token)).andExpect(status().isOk());
		mvc.perform(delete("/users/{id}", SOMEONE_ELSE).headers(token)).andExpect(status().isNoContent());
	}

	@Test
	void userManagesOnlyItself() throws Exception {
		HttpHeaders token = bearer(token(ME, "USER"));

		mvc.perform(get("/users/{id}", ME).headers(token)).andExpect(status().isOk());
		mvc.perform(update(ME).headers(token)).andExpect(status().isOk());
		mvc.perform(delete("/users/{id}", ME).headers(token)).andExpect(status().isNoContent());

		mvc.perform(get("/users/{id}", SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
		mvc.perform(update(SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
		mvc.perform(delete("/users/{id}", SOMEONE_ELSE).headers(token)).andExpect(status().isForbidden());
	}

	@Test
	void userCannotListUsers() throws Exception {
		mvc.perform(get("/users").headers(bearer(token(ME, "USER")))).andExpect(status().isForbidden());
	}

	// ---- 5. Roles ----------------------------------------------------------------------------------------

	@Test
	void validTokenWithUnknownRoleIsForbidden() throws Exception {
		HttpHeaders token = bearer(token(ME, "GUEST"));

		mvc.perform(get("/users").headers(token)).andExpect(status().isForbidden());
		mvc.perform(get("/remittances/users/{id}/balance", ME).headers(token)).andExpect(status().isForbidden());
	}

	// ---- helpers -----------------------------------------------------------------------------------------

	private static List<MockHttpServletRequestBuilder> protectedRequests() {
		return List.of(
				get("/users"),
				get("/users/{id}", ME),
				update(ME),
				delete("/users/{id}", ME),
				get("/remittances/users/{id}/balance", ME),
				get("/remittances/users/{id}/preview", ME).param("usdAmount", "10.00"),
				buy(ME),
				reverse(ME));
	}

	private static MockHttpServletRequestBuilder buy(String userId) {
		return post("/remittances/buy").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + userId + "\",\"amountBrl\":100.00,\"isSameAccount\":true}");
	}

	private static MockHttpServletRequestBuilder reverse(String userId) {
		return post("/remittances/reverse").contentType(MediaType.APPLICATION_JSON)
				.content("{\"userId\":\"" + userId + "\",\"amountUsd\":10.00}");
	}

	private static MockHttpServletRequestBuilder update(String userId) {
		return put("/users/{id}", userId).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Maria Silva\",\"email\":\"maria@mail.com\"}");
	}

	private String token(String subject, String role) {
		return sign(encoder, subject, role, Instant.now().plusSeconds(3600));
	}

	private static String sign(JwtEncoder jwtEncoder, String subject, String role, Instant expiresAt) {
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer("inter-remittance").subject(subject)
				.issuedAt(expiresAt.minusSeconds(3600)).expiresAt(expiresAt).claim(TokenService.ROLES_CLAIM, List.of(role)).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
	}

	private static HttpHeaders bearer(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

}
