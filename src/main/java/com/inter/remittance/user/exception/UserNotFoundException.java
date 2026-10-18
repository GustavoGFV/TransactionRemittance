package com.inter.remittance.user.exception;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(UUID id) {
		super("User %s not found".formatted(id));
	}

}
