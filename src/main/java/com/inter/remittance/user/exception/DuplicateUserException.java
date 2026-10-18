package com.inter.remittance.user.exception;

public class DuplicateUserException extends RuntimeException {

	public DuplicateUserException(String field) {
		super("A user with this %s already exists".formatted(field));
	}

}
