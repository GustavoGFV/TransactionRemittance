package com.inter.remittance.remittance.exception;

public class RecipientNotFoundException extends RuntimeException {

	public RecipientNotFoundException() {
		super("No user registered with the recipient document");
	}

}
