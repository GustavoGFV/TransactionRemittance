package com.inter.remittance.remittance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.service.QuotationService;
import com.inter.remittance.remittance.dto.BuyRemittanceRequest;
import com.inter.remittance.remittance.dto.PreviewResponse;
import com.inter.remittance.remittance.dto.RemittanceResponse;
import com.inter.remittance.remittance.dto.ReverseRemittanceRequest;

class RemittanceServiceTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
	// Two bulletins in the day: the last one is the closing quotation
	private static final List<BuyQuotation> BULLETINS = List.of(
			new BuyQuotation(new BigDecimal("5.0000")), new BuyQuotation(new BigDecimal("5.0123")));

	private QuotationService quotationService;
	private RemittanceTransactionService transactionService;
	private RemittanceService service;

	@BeforeEach
	void setUp() {
		quotationService = mock(QuotationService.class);
		transactionService = mock(RemittanceTransactionService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-19T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
		service = new RemittanceService(quotationService, transactionService, Runnable::run, clock);
		when(quotationService.findBuyQuotations(TODAY)).thenReturn(CompletableFuture.completedFuture(BULLETINS));
	}

	@Test
	void previewUsesTheLastBulletinAndRoundsTheCostUp() {
		UUID userId = UUID.randomUUID();

		PreviewResponse preview = service.preview(userId, new BigDecimal("10.00")).join();

		assertThat(preview.quotation()).isEqualByComparingTo("5.0123");
		assertThat(preview.amountBrl()).isEqualByComparingTo("50.13"); // 50.123 rounded up
		verify(transactionService).requireUser(userId);
	}

	@Test
	void buyDelegatesToTheTransactionWithTheCurrentQuotation() {
		BuyRemittanceRequest request = new BuyRemittanceRequest(UUID.randomUUID(), new BigDecimal("100.00"), true, null);
		RemittanceResponse expected = mock(RemittanceResponse.class);
		when(transactionService.buy(any(), any())).thenReturn(expected);

		assertThat(service.buy(request).join()).isSameAs(expected);
		verify(transactionService).buy(request, new BigDecimal("5.0123"));
	}

	@Test
	void reverseDelegatesToTheTransactionWithTheCurrentQuotation() {
		ReverseRemittanceRequest request = new ReverseRemittanceRequest(UUID.randomUUID(), new BigDecimal("10.00"));
		RemittanceResponse expected = mock(RemittanceResponse.class);
		when(transactionService.reverse(any(), any())).thenReturn(expected);

		assertThat(service.reverse(request).join()).isSameAs(expected);
		verify(transactionService).reverse(request, new BigDecimal("5.0123"));
	}

}
