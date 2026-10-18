package com.inter.remittance.quotation.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.inter.remittance.quotation.dto.BuyQuotation;
import com.inter.remittance.quotation.service.QuotationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/quotations")
@Tag(name = "Quotation", description = "Dollar quotations from the BCB PTAX API")
public class QuotationController {

	private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("MM-dd-uuuu").withResolverStyle(ResolverStyle.STRICT);

	private final QuotationService quotationService;

	public QuotationController(QuotationService quotationService) {
		this.quotationService = quotationService;
	}

	/**
	 * Endpoint to get the buy quotations for a specific date.
	 * 
	 * @param date the date for which to retrieve the buy quotations
	 * @return a CompletableFuture containing a list of BuyQuotation objects
	 */
	@GetMapping
	@Operation(summary = "Get dollar buy quotation",
			description = "Returns the buy quotation (cotacaoCompra) for each PTAX bulletin of the given day. "
					+ "Weekends and holidays have no bulletin, so the last business day before the date is used.")
	@ApiResponse(responseCode = "200", description = "Buy quotations of the day (or of the last business day)")
	@ApiResponse(responseCode = "400", description = "Missing or malformed date",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "No quotation found in the recent business days",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "BCB API unavailable (circuit breaker open or retries exhausted)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public CompletableFuture<List<BuyQuotation>> getBuyQuotations(
			@Parameter(description = "Quotation date in the BCB OData format MM-dd-yyyy, wrapped in single quotes (the quotes are optional)",
					required = true, example = "'10-01-2026'")
			@RequestParam("date") String date) {
		return quotationService.findBuyQuotations(parseBcbDate(date));
	}

	// BCB style: '10-01-2026' (single quotes); the quotes are optional, any other format is rejected
	private static LocalDate parseBcbDate(String value) {
		String raw = value.strip();
		if (raw.length() > 1 && raw.startsWith("'") && raw.endsWith("'")) {
			raw = raw.substring(1, raw.length() - 1);
		}
		try {
			return LocalDate.parse(raw, BCB_DATE);
		}
		catch (DateTimeParseException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date, expected 'MM-dd-yyyy' with single quotes");
		}
	}

}
