package com.inter.remittance.quotation.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.inter.remittance.quotation.client.BcbPtaxClient;
import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.exception.QuotationNotFoundException;
import com.inter.remittance.quotation.mapper.QuotationMapper;

@Service
public class QuotationService {

	private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");
	private static final String JSON_FORMAT = "json";
	private static final int MAX_BUSINESS_DAY_ATTEMPTS = 5;

	private final BcbPtaxClient client;
	private final QuotationMapper mapper;
	private final Executor executor;

	public QuotationService(BcbPtaxClient client, QuotationMapper mapper,
			@Qualifier("quotationExecutor") Executor executor) {
		this.client = client;
		this.mapper = mapper;
		this.executor = executor;
	}

	public CompletableFuture<List<BuyQuotation>> findBuyQuotations(LocalDate date) {
		return CompletableFuture.supplyAsync(() -> findOnLastBusinessDay(date), executor);
	}

	// Weekends are resolved arithmetically (no BCB call); the loop only runs again for holidays,
	// when BCB returns an empty list for a weekday.
	private List<BuyQuotation> findOnLastBusinessDay(LocalDate date) {
		LocalDate current = adjustWeekend(date);
		for (int attempt = 0; attempt < MAX_BUSINESS_DAY_ATTEMPTS; attempt++) {
			List<BuyQuotation> quotations = mapper.toBuyQuotations(client.findDollarQuotationByDay(params(current)));
			if (!quotations.isEmpty()) {
				return quotations;
			}
			current = adjustWeekend(current.minusDays(1));
		}
		throw new QuotationNotFoundException(date, MAX_BUSINESS_DAY_ATTEMPTS);
	}

	private static LocalDate adjustWeekend(LocalDate date) {
		return switch (date.getDayOfWeek()) {
			case SATURDAY -> date.minusDays(1);
			case SUNDAY -> date.minusDays(2);
			default -> date;
		};
	}

	private static Map<String, String> params(LocalDate date) {
		return Map.of(
				"@dataCotacao", quote(date),
				"$format", JSON_FORMAT);
	}

	// BCB expects the date wrapped in single quotes: '10-01-2026'
	private static String quote(LocalDate date) {
		return "'" + BCB_DATE.format(date) + "'";
	}

}
