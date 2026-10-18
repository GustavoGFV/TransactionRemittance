package com.inter.remittance.remittance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.inter.remittance.remittance.exception.DailyLimitExceededException;
import com.inter.remittance.remittance.exception.InvalidRemittanceException;
import com.inter.remittance.remittance.exception.RecipientNotFoundException;
import com.inter.remittance.user.exception.InsufficientBalanceException;

@RestControllerAdvice(basePackages = "com.inter.remittance.remittance")
public class RemittanceExceptionHandler {

	@ExceptionHandler({ InsufficientBalanceException.class, DailyLimitExceededException.class })
	ProblemDetail handleBusinessRule(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler(RecipientNotFoundException.class)
	ProblemDetail handleRecipientNotFound(RecipientNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(InvalidRemittanceException.class)
	ProblemDetail handleInvalid(InvalidRemittanceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}
