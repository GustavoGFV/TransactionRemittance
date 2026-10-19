package com.inter.remittance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;

/*
 * Index of the scenario (tests run in order and share state):
 *  Setup - CPF user with R$ 12,000 and CNPJ user with R$ 0, quotation 5.00.
 *  1. CNPJ without BRL balance cannot buy USD (422, balances unchanged).
 *  2. CPF buys USD on its own account (R$ 2,000), sends R$ 2,000 to the CNPJ, and a third purchase of
 *     R$ 7,000 exceeds the R$ 10,000 daily limit (422).
 *  3. CNPJ converts 200 USD back to BRL (R$ 1,000), updating both currency balances.
 *  4. On a simulated Saturday the BCB is queried with the previous Friday and its quotation is applied.
 */
/**
 * Business scenario that runs in order and shares state between tests, like a real day of operations:
 * a CPF user with R$ 12,000 and a CNPJ user with R$ 0, on a weekday quotation of 5.00 (Friday: 5.20).
 */
@Import({ TestcontainersConfiguration.class, CrossBorderScenarioIT.MutableClockConfig.class })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.rate-limit.auth-max-requests=100000", "app.rate-limit.quotations-max-requests=100000" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossBorderScenarioIT {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
	private static final String PASSWORD = "S3nh@Forte2026";
	private static final BigDecimal WEEKDAY_RATE = new BigDecimal("5.0000");
	private static final BigDecimal FRIDAY_RATE = new BigDecimal("5.2000");
	// 2026-10-17 is a Saturday: the BCB has no bulletin, so the Friday 10-16 quotation must be used
	private static final Instant SATURDAY = Instant.parse("2026-10-17T15:00:00Z");
	private static final String FRIDAY_PARAM = "'10-16-2026'";

	@LocalServerPort
	private int port;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private MutableClock clock;

	@MockitoBean
	private BcbPtaxClient bcbClient;

	private RestClient http;
	private String cpfId;
	private String cpfEmail;
	private String cpfDocument;
	private String cnpjId;
	private String cnpjEmail;
	private String cnpjDocument;

	@BeforeAll
	void createUsers() {
		http = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { }).build();
		cpfDocument = DocumentGenerator.cpf();
		cnpjDocument = DocumentGenerator.cnpj();
		cpfEmail = "cpf-" + System.nanoTime() + "@mail.com";
		cnpjEmail = "cnpj-" + System.nanoTime() + "@mail.com";
		cpfId = signUp("User CPF", cpfEmail, cpfDocument, "12000.00");
		cnpjId = signUp("User CNPJ", cnpjEmail, cnpjDocument, "0");
	}

	@BeforeEach
	void setUp() {
		reset(bcbClient);
		cacheManager.getCache("quotations").clear();
		clock.useSystemTime();
		when(bcbClient.findDollarQuotationByDay(anyMap())).thenReturn(quotation(WEEKDAY_RATE));
	}

	@Test
	@Order(1)
	void cnpjWithoutBalanceCannotBuyUsd() {
		ResponseEntity<Map<String, Object>> response = buy(cnpjToken(), cnpjId, "100.00", true, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
		assertThat(balance(cnpjToken(), cnpjId)).containsEntry("balanceBrl", 0.0).containsEntry("balanceUsd", 0.0);
	}

	@Test
	@Order(2)
	void cpfTransfersToOwnAccountAndToTheCnpjUntilTheDailyLimitBlocksTheNextBuy() {
		String token = cpfToken();

		assertThat(buy(token, cpfId, "2000.00", true, null).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(buy(token, cpfId, "2000.00", false, cnpjDocument).getStatusCode()).isEqualTo(HttpStatus.OK);

		// R$ 8,000 remain, but 2,000 + 2,000 + 7,000 = 11,000 exceeds the R$ 10,000 CPF daily limit
		ResponseEntity<Map<String, Object>> overLimit = buy(token, cpfId, "7000.00", true, null);
		assertThat(overLimit.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

		assertThat(balance(token, cpfId)).containsEntry("balanceBrl", 8000.0).containsEntry("balanceUsd", 400.0);
		assertThat(balance(cnpjToken(), cnpjId)).containsEntry("balanceUsd", 400.0).containsEntry("balanceBrl", 0.0);
	}

	@Test
	@Order(3)
	void cnpjConvertsPartOfItsUsdBackToBrl() {
		// 200 USD at 5.00 = R$ 1,000, half of the 400 USD received (R$ 2,000)
		ResponseEntity<Map<String, Object>> response = http.post().uri("/remittances/reverse")
				.headers(headers -> headers.setBearerAuth(cnpjToken())).contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("userId", cnpjId, "amountUsd", "200.00")).retrieve().toEntity(MAP);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(balance(cnpjToken(), cnpjId)).containsEntry("balanceBrl", 1000.0).containsEntry("balanceUsd", 200.0);
	}

	@Test
	@Order(4)
	void weekendOperationUsesTheLastBusinessDayQuotation() {
		String token = cnpjToken();
		clock.fixAt(SATURDAY);
		when(bcbClient.findDollarQuotationByDay(anyMap())).thenAnswer(invocation -> {
			Map<String, String> params = invocation.getArgument(0);
			return FRIDAY_PARAM.equals(params.get("@dataCotacao")) ? quotation(FRIDAY_RATE)
					: new BcbQuotationResponse(List.of());
		});

		ResponseEntity<Map<String, Object>> response = buy(token, cnpjId, "1000.00", true, null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		// 1,000 / 5.20 = 192.30 USD (rounded down) on top of the 200 USD already held
		assertThat(response.getBody()).containsEntry("quotation", 5.2).containsEntry("amountUsd", 192.3);
		assertThat(balance(token, cnpjId)).containsEntry("balanceBrl", 0.0).containsEntry("balanceUsd", 392.3);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(bcbClient, atLeastOnce()).findDollarQuotationByDay(captor.capture());
		assertThat(captor.getAllValues()).extracting(params -> params.get("@dataCotacao")).containsOnly(FRIDAY_PARAM);
	}

	private String signUp(String name, String email, String document, String brl) {
		ResponseEntity<Map<String, Object>> response = http.post().uri("/users").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("name", name, "email", email, "document", document, "password", PASSWORD,
						"balanceBrl", new BigDecimal(brl)))
				.retrieve().toEntity(MAP);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String path = response.getHeaders().getLocation().getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private String cpfToken() {
		return login(cpfEmail);
	}

	private String cnpjToken() {
		return login(cnpjEmail);
	}

	private String login(String email) {
		Map<String, Object> body = http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("email", email, "password", PASSWORD)).retrieve().body(MAP);
		return (String) body.get("accessToken");
	}

	private ResponseEntity<Map<String, Object>> buy(String token, String user, String amountBrl, boolean sameAccount,
			String recipientDocument) {
		Map<String, Object> body = new HashMap<>(Map.of("userId", user, "amountBrl", new BigDecimal(amountBrl),
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

	private static BcbQuotationResponse quotation(BigDecimal buy) {
		return new BcbQuotationResponse(List.of(new BcbQuotationItem(buy, buy, "2026-10-14 13:05:00")));
	}

	/** Clock that follows the system time until a test pins it, so tokens keep a real validity. */
	static class MutableClock extends Clock {

		private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
		private volatile Instant fixed;

		void fixAt(Instant instant) {
			this.fixed = instant;
		}

		void useSystemTime() {
			this.fixed = null;
		}

		@Override
		public ZoneId getZone() {
			return ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.system(zone);
		}

		@Override
		public Instant instant() {
			Instant pinned = fixed;
			return pinned != null ? pinned : Instant.now();
		}

	}

	@TestConfiguration
	static class MutableClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock();
		}

	}

}
