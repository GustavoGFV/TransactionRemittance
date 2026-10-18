package com.inter.remittance.quotation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.client.dto.BcbQuotationItem;
import com.inter.remittance.quotation.client.dto.BcbQuotationResponse;
import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.exception.QuotationNotFoundException;
import com.inter.remittance.quotation.mapper.QuotationMapper;

class QuotationServiceTest {

	// 2026-10-16 is a Friday, 2026-10-17 a Saturday, 2026-10-18 a Sunday and 2026-10-12 a Monday
	private static final BcbQuotationResponse WITH_QUOTATION = new BcbQuotationResponse(
			List.of(new BcbQuotationItem(new BigDecimal("5.0123"), new BigDecimal("5.0200"), "2026-10-16 13:05:00.0")));
	private static final BcbQuotationResponse EMPTY = new BcbQuotationResponse(List.of());

	private BcbPtaxClient client;
	private QuotationService service;
	private SimpleMeterRegistry meters;

	@BeforeEach
	void setUp() {
		client = mock(BcbPtaxClient.class);
		meters = new SimpleMeterRegistry();
		service = new QuotationService(client, Mappers.getMapper(QuotationMapper.class), Runnable::run,
				new LastKnownQuotationStore(Duration.ofHours(24), Clock.systemUTC()), meters);
	}

	@ParameterizedTest
	@CsvSource({ "2026-10-16, 10-16-2026", "2026-10-17, 10-16-2026", "2026-10-18, 10-16-2026" })
	void queriesTheLastBusinessDay(LocalDate requested, String expected) throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(WITH_QUOTATION);

		service.findBuyQuotations(requested).get();

		assertThat(requestedDates()).containsExactly("'" + expected + "'");
	}

	@Test
	void returnsOnlyTheBuyQuotation() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(WITH_QUOTATION);

		List<BuyQuotation> quotations = service.findBuyQuotations(LocalDate.of(2026, 10, 16)).get();

		assertThat(quotations).extracting(BuyQuotation::cotacaoCompra).containsExactly(new BigDecimal("5.0123"));
	}

	@Test
	void walksBackOverHolidaysUntilAQuotationExists() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(EMPTY, WITH_QUOTATION);

		List<BuyQuotation> quotations = service.findBuyQuotations(LocalDate.of(2026, 10, 12)).get();

		assertThat(quotations).hasSize(1);
		assertThat(requestedDates()).containsExactly("'10-12-2026'", "'10-09-2026'");
	}

	@Test
	void failsWhenNoQuotationIsFoundWithinTheAllowedAttempts() {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(EMPTY);

		assertThatThrownBy(() -> service.findBuyQuotations(LocalDate.of(2026, 10, 16)).get())
				.isInstanceOf(ExecutionException.class)
				.hasCauseInstanceOf(QuotationNotFoundException.class);
		verify(client, times(5)).findDollarQuotationByDay(anyMap());
	}

	@Test
	void sendsTheJsonFormatAndQuotesTheDate() {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(WITH_QUOTATION);

		service.findBuyQuotations(LocalDate.of(2026, 10, 16));

		assertThat(captureParams().getFirst())
				.containsEntry("$format", "json")
				.containsEntry("@dataCotacao", "'10-16-2026'");
	}

	/*
	 * Stale-if-error (BCB outage):
	 *  1. a successful lookup is remembered as the last known quotation;
	 *  2. when the BCB then fails, that quotation is served and quotation.stale.served is incremented;
	 *  3. with nothing remembered, the original failure is propagated (no invented rate);
	 *  4. "no quotation found" is a business answer from a healthy BCB and is never masked by stale data.
	 */
	@Test
	void servesTheLastKnownQuotationWhenTheBcbFails() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(WITH_QUOTATION)
				.thenThrow(new IllegalStateException("BCB down"));
		service.findBuyQuotations(LocalDate.of(2026, 10, 16)).get();

		List<BuyQuotation> stale = service.findBuyQuotations(LocalDate.of(2026, 10, 15)).get();

		assertThat(stale).extracting(BuyQuotation::cotacaoCompra).containsExactly(new BigDecimal("5.0123"));
		assertThat(meters.counter("quotation.stale.served").count()).isEqualTo(1.0);
	}

	@Test
	void propagatesTheFailureWhenThereIsNoLastKnownQuotation() {
		when(client.findDollarQuotationByDay(anyMap())).thenThrow(new IllegalStateException("BCB down"));

		assertThatThrownBy(() -> service.findBuyQuotations(LocalDate.of(2026, 10, 16)).get())
				.isInstanceOf(ExecutionException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(meters.counter("quotation.stale.served").count()).isZero();
	}

	@Test
	void doesNotMaskQuotationNotFoundWithStaleData() throws Exception {
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(WITH_QUOTATION);
		service.findBuyQuotations(LocalDate.of(2026, 10, 16)).get();
		when(client.findDollarQuotationByDay(anyMap())).thenReturn(EMPTY);

		assertThatThrownBy(() -> service.findBuyQuotations(LocalDate.of(2026, 10, 14)).get())
				.hasCauseInstanceOf(QuotationNotFoundException.class);
	}

	private List<String> requestedDates() {		return captureParams().stream().map(params -> params.get("@dataCotacao")).toList();
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, String>> captureParams() {
		ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
		verify(client, atLeastOnce()).findDollarQuotationByDay(captor.capture());
		return captor.getAllValues();
	}

}
