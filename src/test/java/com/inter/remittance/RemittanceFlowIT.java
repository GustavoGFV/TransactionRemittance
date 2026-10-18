package com.inter.remittance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.service.LastKnownQuotationStore;
import io.micrometer.core.instrument.MeterRegistry;
import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;
import com.inter.remittance.quotation.exception.QuotationUnavailableException;

/*
 * Index of the integration scenarios (real HTTP, real PostgreSQL; only the BCB client is mocked):
 *  1. Security      - endpoints reject missing tokens; roles (ADMIN/USER) and ownership are enforced; sign-up is public.
 *  2. Users         - invalid and duplicated CPF/CNPJ/e-mail are rejected; wrong login credentials return 401.
 *  3. Daily limit   - external and same-account purchases both consume the PF/PJ daily cap.
 *  4. Rollback      - a failed remittance leaves every balance and the limit counter untouched.
 *  5. Reverse       - USD back to BRL, to the sender or to another registered user.
 *  6. Quotation     - one BCB call per day (cache), BCB date format, outage answered with 503 and never cached.
 *  7. Resilience    - stale-if-error serves the last known quotation when the BCB fails; cache metrics are exposed.
 */
/**
 * End-to-end flow over real HTTP against a real PostgreSQL (Testcontainers). Only the BCB client is mocked.
 * Each test creates its own users with unique documents, so tests do not depend on each other.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RemittanceFlowIT {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
	private static final BigDecimal QUOTATION = new BigDecimal("5.0000");

	@LocalServerPort
	private int port;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private LastKnownQuotationStore lastKnownQuotation;

	@Autowired
	private MeterRegistry meterRegistry;

	@MockitoBean
	private BcbPtaxClient bcbClient;

	private final Map<String, String> emails = new java.util.concurrent.ConcurrentHashMap<>();

	private RestClient http;

	@BeforeEach
	void setUp() {
		reset(bcbClient);
		cacheManager.getCache("quotations").clear();
		lastKnownQuotation.clear();
		when(bcbClient.findDollarQuotationByDay(anyMap())).thenReturn(
				new BcbQuotationResponse(List.of(new BcbQuotationItem(QUOTATION, new BigDecimal("5.0100"), "2026-10-14 13:05:00"))));
		http = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { })
				.build();
	}

	@Test
	void securedEndpointsRejectRequestsWithoutToken() {
		assertThat(http.get().uri("/users/{id}", "00000000-0000-0000-0000-000000000000").retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void userValidationRejectsInvalidAndDuplicatedDocuments() {
		String token = token();
		String unique = unique();

		assertThat(createUser(token, "Invalid", unique + "@mail.com", "111.111.111-11", "0", "0").getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);

		String document = uniqueCpf();
		assertThat(createUser(token, "First", unique + "@mail.com", document, "0", "0").getStatusCode())
				.isEqualTo(HttpStatus.CREATED);
		assertThat(createUser(token, "Second", "other" + unique + "@mail.com", document, "0", "0").getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void externalTransferConsumesAndEnforcesTheDailyLimit() {
		String token = token();
		String sender = createUserId(token, uniqueCpf(), "50000.00", "0");
		String recipientDocument = uniqueCnpj();
		String recipient = createUserId(token, recipientDocument, "0", "0");

		// R$ 6,000 + R$ 4,000 = exactly the R$ 10,000 limit of an individual (CPF)
		assertThat(buy(login(sender), sender, "6000.00", false, recipientDocument).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(buy(login(sender), sender, "4000.00", false, recipientDocument).getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<Map<String, Object>> overLimit = buy(login(sender), sender, "1.00", false, recipientDocument);
		assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

		assertThat(balance(login(sender), sender)).containsEntry("balanceBrl", 40000.0);
		assertThat(balance(login(recipient), recipient)).containsEntry("balanceUsd", 2000.0);
	}

	@Test
	void sameAccountPurchaseAlsoCountsTowardTheDailyLimit() {
		String token = token();
		String user = createUserId(token, uniqueCpf(), "20000.00", "0");

		assertThat(buy(login(user), user, "6000.00", true, null).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(buy(login(user), user, "4000.01", true, null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

		assertThat(balance(login(user), user)).containsEntry("balanceBrl", 14000.0).containsEntry("balanceUsd", 1200.0);
	}

	@Test
	void loginRejectsWrongPasswordAndUnknownEmail() {
		String user = createUserId(token(), uniqueCpf(), "0", "0");

		assertThat(http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", emails.get(user), "password", "wrong-password")).retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", "nobody@mail.com", "password", "S3nh@Forte2026")).retrieve().toBodilessEntity()
				.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void userTokenOnlyOperatesOnTheUsersOwnRemittances() {
		String admin = token();
		String alice = createUserId(admin, uniqueCpf(), "1000.00", "0");
		String bob = createUserId(admin, uniqueCpf(), "1000.00", "0");

		// Bob's token cannot read or spend Alice's money
		assertThat(http.get().uri("/remittances/users/{id}/balance", alice)
				.headers(headers -> headers.setBearerAuth(login(bob))).retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(buy(login(bob), alice, "10.00", true, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		// The ADMIN token manages users but cannot move money
		assertThat(buy(admin, alice, "10.00", true, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(balance(login(alice), alice)).containsEntry("balanceBrl", 1000.0);
	}

	@Test
	void userManagementRequiresTheRightRole() {
		String admin = token();
		String alice = createUserId(admin, uniqueCpf(), "0", "0");
		String bob = createUserId(admin, uniqueCpf(), "0", "0");

		assertThat(http.get().uri("/users").headers(headers -> headers.setBearerAuth(login(alice))).retrieve()
				.toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(http.get().uri("/users").headers(headers -> headers.setBearerAuth(admin)).retrieve()
				.toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(http.get().uri("/users/{id}", alice).headers(headers -> headers.setBearerAuth(login(alice)))
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(http.get().uri("/users/{id}", bob).headers(headers -> headers.setBearerAuth(login(alice)))
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(http.delete().uri("/users/{id}", bob).headers(headers -> headers.setBearerAuth(login(alice)))
				.retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void signUpIsPublic() {
		ResponseEntity<Map<String, Object>> response = http.post().uri("/users").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", "Self Signup", "email", unique() + "@mail.com", "document", uniqueCpf(),
						"password", "S3nh@Forte2026"))
				.retrieve().toEntity(MAP);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}
	@Test
	void failedRemittanceRollsBackEveryChange() {
		String token = token();
		String sender = createUserId(token, uniqueCpf(), "100.00", "0");
		String recipientDocument = uniqueCpf();
		String recipient = createUserId(token, recipientDocument, "0", "0");

		assertThat(buy(login(sender), sender, "500.00", false, recipientDocument).getStatusCode())
				.isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

		assertThat(balance(login(sender), sender)).containsEntry("balanceBrl", 100.0).containsEntry("balanceUsd", 0.0);
		assertThat(balance(login(recipient), recipient)).containsEntry("balanceBrl", 0.0).containsEntry("balanceUsd", 0.0);

		// The failed attempt must not have left a daily counter behind: the full limit is still available
		String rich = createUserId(token, uniqueCpf(), "10000.00", "0");
		assertThat(buy(login(rich), rich, "10000.00", false, recipientDocument).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void reverseRemittanceConvertsUsdBackToBrl() {
		String token = token();
		String user = createUserId(token, uniqueCpf(), "0", "100.00");

		ResponseEntity<Map<String, Object>> response = http.post().uri("/remittances/reverse")
				.headers(headers -> headers.setBearerAuth(login(user))).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("userId", user, "amountUsd", "40.00")).retrieve().toEntity(MAP);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(balance(login(user), user)).containsEntry("balanceBrl", 200.0).containsEntry("balanceUsd", 60.0);
	}

	@Test
	void reverseCanCreditAnotherRegisteredUser() {
		String token = token();
		String recipientDocument = uniqueCpf();
		String sender = createUserId(token, uniqueCnpj(), "0", "100.00");
		String recipient = createUserId(token, recipientDocument, "0", "0");

		ResponseEntity<Map<String, Object>> response = http.post().uri("/remittances/reverse")
				.headers(headers -> headers.setBearerAuth(login(sender))).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("userId", sender, "amountUsd", "40.00", "recipientDocument", recipientDocument))
				.retrieve().toEntity(MAP);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(balance(login(sender), sender)).containsEntry("balanceUsd", 60.0).containsEntry("balanceBrl", 0.0);
		assertThat(balance(login(recipient), recipient)).containsEntry("balanceBrl", 200.0);
	}

	@Test
	void quotationIsFetchedFromTheBcbOnlyOncePerDay() {
		String token = token();
		String user = createUserId(token, uniqueCpf(), "0", "0");

		for (int i = 0; i < 3; i++) {
			ResponseEntity<Map<String, Object>> preview = http.get()
					.uri("/remittances/users/{id}/preview?usdAmount=10.00", user)
					.headers(headers -> headers.setBearerAuth(login(user))).retrieve().toEntity(MAP);
			assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(preview.getBody()).containsEntry("amountBrl", 50.0);
		}

		verify(bcbClient, times(1)).findDollarQuotationByDay(anyMap());
	}

	@Test
	void quotationEndpointAcceptsTheBcbDateFormatWithAndWithoutSingleQuotes() {
		assertThat(http.get().uri("/quotations?date={d}", "'10-01-2026'").retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(http.get().uri("/quotations?date={d}", "10-01-2026").retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.OK);
		assertThat(http.get().uri("/quotations?date={d}", "2026-10-01").retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void bcbOutageIsReportedAsServiceUnavailableAndNotCached() {
		String token = token();
		String user = createUserId(token, uniqueCpf(), "0", "0");
		when(bcbClient.findDollarQuotationByDay(anyMap()))
				.thenThrow(new QuotationUnavailableException("BCB down", new RuntimeException()));

		assertThat(preview(login(user), user).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		verify(bcbClient, times(1)).findDollarQuotationByDay(anyMap());

		// doReturn: re-stubbing with when(...) would invoke the mock, which still throws
		doReturn(new BcbQuotationResponse(List.of(new BcbQuotationItem(QUOTATION, QUOTATION, "2026-10-14 13:05:00"))))
				.when(bcbClient).findDollarQuotationByDay(anyMap());
		assertThat(preview(login(user), user).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void lastKnownQuotationIsServedWhenTheBcbFailsAfterASuccess() {
		String token = token();
		String user = createUserId(token, uniqueCpf(), "0", "0");
		assertThat(preview(login(user), user).getStatusCode()).isEqualTo(HttpStatus.OK);
		double staleBefore = meterRegistry.counter("quotation.stale.served").count();

		cacheManager.getCache("quotations").clear();
		when(bcbClient.findDollarQuotationByDay(anyMap()))
				.thenThrow(new QuotationUnavailableException("BCB down", new RuntimeException()));

		ResponseEntity<Map<String, Object>> response = preview(login(user), user);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).containsEntry("amountBrl", 50.0);
		assertThat(meterRegistry.counter("quotation.stale.served").count()).isEqualTo(staleBefore + 1);
		assertThat(meterRegistry.find("cache.gets").tag("cache", "quotations").meters()).isNotEmpty();
	}

	private ResponseEntity<Map<String, Object>> preview(String token, String user) {
		return http.get().uri("/remittances/users/{id}/preview?usdAmount=10.00", user)
				.headers(headers -> headers.setBearerAuth(token)).retrieve().toEntity(MAP);
	}

	private String token() {
		Map<String, Object> body = http.post().uri("/auth/token").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("clientId", "remittance-client", "clientSecret", "remittance-secret"))
				.retrieve().body(MAP);
		return (String) body.get("accessToken");
	}

	private ResponseEntity<Map<String, Object>> createUser(String token, String name, String email, String document,
			String brl, String usd) {
		return http.post().uri("/users").headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", name, "email", email, "document", document, "password", "S3nh@Forte2026",
						"balanceBrl", new BigDecimal(brl), "balanceUsd", new BigDecimal(usd)))
				.retrieve().toEntity(MAP);
	}

	private String createUserId(String token, String document, String brl, String usd) {
		String email = unique() + "@mail.com";
		ResponseEntity<Map<String, Object>> response = createUser(token, "User", email, document, brl, usd);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String id = (String) response.getBody().get("id");
		emails.put(id, email);
		return id;
	}

	// USER token of a user created by createUserId
	private String login(String userId) {
		Map<String, Object> body = http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", emails.get(userId), "password", "S3nh@Forte2026")).retrieve().body(MAP);
		return (String) body.get("accessToken");
	}

	private ResponseEntity<Map<String, Object>> buy(String token, String user, String amountBrl, boolean sameAccount,
			String recipientDocument) {
		Map<String, Object> body = new java.util.HashMap<>(Map.of("userId", user, "amountBrl", new BigDecimal(amountBrl),
				"isSameAccount", sameAccount));
		if (recipientDocument != null) {
			body.put("recipientDocument", recipientDocument);
		}
		return http.post().uri("/remittances/buy").headers(headers -> headers.setBearerAuth(token))
				.contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(MAP);
	}

	private Map<String, Object> balance(String token, String user) {
		return http.get().uri("/remittances/users/{id}/balance", user).headers(headers -> headers.setBearerAuth(token))
				.retrieve().body(MAP);
	}

	private static String unique() {
		return java.util.UUID.randomUUID().toString().substring(0, 12);
	}

	// Generated documents carry valid check digits, so each test owns a fresh document
	private static String uniqueCpf() {
		return com.inter.remittance.DocumentGenerator.cpf();
	}

	private static String uniqueCnpj() {
		return com.inter.remittance.DocumentGenerator.cnpj();
	}

}
