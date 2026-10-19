package com.inter.remittance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;

/*
 * Index of the rate limiting scenarios over real HTTP (limits lowered to 3 credential calls and 2 quotation calls):
 *  1. Login attempts: the 4th call of the window is answered with 429 (Retry-After) before reaching the controller.
 *  2. Quotations: the 3rd call of the window is answered with 429.
 *  3. Health and other endpoints are not rate limited.
 * Each test uses a different bucket (credentials, quotations, none), so they do not interfere with each other.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"app.rate-limit.window=10m", "app.rate-limit.auth-max-requests=3", "app.rate-limit.quotations-max-requests=2" })
class RateLimitIT {

	@LocalServerPort
	private int port;

	@MockitoBean
	private BcbPtaxClient bcbClient;

	private RestClient http;

	@BeforeEach
	void setUp() {
		http = RestClient.builder().baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> { }).build();
		when(bcbClient.findDollarQuotationByDay(anyMap())).thenReturn(new BcbQuotationResponse(
				List.of(new BcbQuotationItem(new BigDecimal("5.0000"), new BigDecimal("5.0100"), "2026-10-14 13:05:00"))));
	}

	@Test
	void loginIsLimitedPerClient() {
		for (int attempt = 1; attempt <= 3; attempt++) {
			assertThat(login().getStatusCode()).as("attempt %d", attempt).isEqualTo(HttpStatus.UNAUTHORIZED);
		}

		ResponseEntity<String> blocked = login();

		assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(blocked.getHeaders().getFirst("Retry-After")).isEqualTo("600");
		assertThat(blocked.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
		assertThat(blocked.getBody()).contains("Too Many Requests");
	}

	@Test
	void quotationsAreLimitedPerClient() {
		assertThat(quotation().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(quotation().getStatusCode()).isEqualTo(HttpStatus.OK);

		assertThat(quotation().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void healthIsNeverLimited() {
		for (int i = 0; i < 10; i++) {
			assertThat(http.get().uri("/actuator/health").retrieve().toBodilessEntity().getStatusCode())
					.isEqualTo(HttpStatus.OK);
		}
	}

	private ResponseEntity<String> login() {
		return http.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body("{\"email\":\"nobody@mail.com\",\"password\":\"wrong-password\"}").retrieve().toEntity(String.class);
	}

	private ResponseEntity<String> quotation() {
		return http.get().uri("/quotations?date='10-14-2026'").retrieve().toEntity(String.class);
	}

}
