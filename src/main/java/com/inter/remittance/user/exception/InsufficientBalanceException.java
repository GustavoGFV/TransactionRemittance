package com.inter.remittance.user.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

	public InsufficientBalanceException(String currency, BigDecimal available, BigDecimal required) {
		super("Insufficient %s balance: available %s, required minimum %s".formatted(currency, available.toPlainString(), required.toPlainString()));
	}

}
