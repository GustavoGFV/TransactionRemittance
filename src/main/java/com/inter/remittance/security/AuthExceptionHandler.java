package com.inter.remittance.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.inter.remittance.security")
public class AuthExceptionHandler {

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

}
