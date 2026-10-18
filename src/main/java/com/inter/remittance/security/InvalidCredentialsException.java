package com.inter.remittance.security;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid client credentials");
	}

}
