package com.inter.remittance.remittance.exception;

import java.math.BigDecimal;

public class DailyLimitExceededException extends RuntimeException {

	public DailyLimitExceededException(BigDecimal limit, BigDecimal remaining) {
		super("Daily limit of R$ %s exceeded; remaining today: R$ %s".formatted(limit.toPlainString(), remaining.toPlainString()));
	}

}
