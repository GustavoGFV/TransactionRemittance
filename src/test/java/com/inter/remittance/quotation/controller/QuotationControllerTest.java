package com.inter.remittance.quotation.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.inter.remittance.quotation.service.QuotationService;
import com.inter.remittance.security.SecurityConfig;

/*
 * Index of the quotation endpoint scenarios (web layer only, service mocked):
 *  1. The BCB date format 'MM-dd-yyyy' is accepted with or without the single quotes.
 *  2. Any other or impossible date is rejected with a 400 RFC 7807 body from the shared exception handler.
 */
@WebMvcTest(QuotationController.class)
@Import(SecurityConfig.class)
class QuotationControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private QuotationService quotationService;

	@ParameterizedTest
	@ValueSource(strings = { "'10-01-2026'", "10-01-2026" })
	void acceptsTheBcbDateWithAndWithoutQuotes(String date) throws Exception {
		when(quotationService.findBuyQuotations(LocalDate.of(2026, 10, 1)))
				.thenReturn(CompletableFuture.completedFuture(List.of()));

		mvc.perform(get("/quotations").param("date", date)).andExpect(request().asyncStarted());

		verify(quotationService).findBuyQuotations(eq(LocalDate.of(2026, 10, 1)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-01", "01-10-2026x", "13-01-2026", "02-30-2026", "abc", "'10-01-2026" })
	void rejectsInvalidDatesWithProblemJson(String date) throws Exception {
		mvc.perform(get("/quotations").param("date", date))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Invalid date, expected 'MM-dd-yyyy' with single quotes"));

		verifyNoInteractions(quotationService);
	}

	@Test
	void missingDateIsABadRequest() throws Exception {
		mvc.perform(get("/quotations")).andExpect(status().isBadRequest());
	}

}
