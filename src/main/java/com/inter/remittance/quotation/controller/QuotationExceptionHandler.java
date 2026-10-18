package com.inter.remittance.quotation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.inter.remittance.quotation.exception.QuotationNotFoundException;
import com.inter.remittance.quotation.exception.QuotationUnavailableException;

// Not scoped to QuotationController: the remittance endpoints also depend on the quotation and must answer 404/503 the same way
@RestControllerAdvice
public class QuotationExceptionHandler {

	@ExceptionHandler(QuotationNotFoundException.class)
	ProblemDetail handleNotFound(QuotationNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(QuotationUnavailableException.class)
	ProblemDetail handleUnavailable(QuotationUnavailableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

}
