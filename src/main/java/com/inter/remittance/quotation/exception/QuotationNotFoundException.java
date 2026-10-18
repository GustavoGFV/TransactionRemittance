package com.inter.remittance.quotation.exception;

import java.time.LocalDate;

public class QuotationNotFoundException extends RuntimeException {

	public QuotationNotFoundException(LocalDate date, int attempts) {
		super("No quotation found for %s after checking %d business days".formatted(date, attempts));
	}

}
